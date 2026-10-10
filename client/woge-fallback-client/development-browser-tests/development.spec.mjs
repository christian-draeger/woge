import { test, expect } from "@playwright/test";

test("multiple tabs refresh only on success and failed builds leave dirty controls usable", async ({ page, context, request }) => {
  await page.goto("/");
  await expect(page.locator("#woge-development-status")).toBeVisible();
  const beforeSave = await page.locator("#build").textContent();
  await request.get("/control?save");
  await expect(page.locator("#build")).not.toHaveText(beforeSave);
  await expect(page.locator("#woge-development-status")).toHaveAttribute("data-phase", "READY");
  const second = await context.newPage();
  await second.goto("http://127.0.0.1:4273/");
  await expect(page.locator("#woge-development-status")).toHaveAttribute("data-phase", "READY");
  await expect(second.locator("#woge-development-status")).toHaveAttribute("data-phase", "READY");
  const initial = await page.locator("#build").textContent();
  await page.getByLabel("Name").fill("unsaved");
  await second.getByLabel("Name").fill("other tab");
  await request.get("/control?fail");
  await expect(page.getByRole("status")).toContainText("Build failed");
  await expect(second.getByRole("status")).toContainText("Build failed");
  await expect(page.locator("#build")).toHaveText(initial);
  await expect(page.getByLabel("Name")).toHaveValue("unsaved");
  await expect(page.locator("#woge-development-status ul")).toContainText("src/main/Page.kt:4:8");
  await expect(page.locator("#woge-development-status img")).toHaveCount(0);
  await request.get("/control?save");
  await expect(page.locator("#build")).not.toHaveText(initial);
  await expect(second.locator("#build")).not.toHaveText(initial);
  await expect(page.getByRole("status")).toHaveText("Ready");
  await expect(page.getByLabel("Name")).toHaveValue("unsaved");
  await expect(second.getByLabel("Name")).toHaveValue("other tab");
  await second.close();
});

test("full refresh preserves opted-in dirty controls, selection and scroll without changing defaults", async ({ page, request }) => {
  await page.goto("/");
  await expect(page.getByRole("status")).toHaveText("Ready");
  await page.getByLabel("Name").fill("unfinished title");
  await page.getByLabel("Notes").fill("unfinished notes");
  await page.locator("#checkbox").check();
  await page.locator("#radio").check();
  await page.locator("#choice").selectOption(["two", "three"]);
  await page.evaluate(() => {
    const input = document.getElementById("name");
    input.focus();
    input.setSelectionRange(2, 8, "backward");
    window.scrollTo(0, 600);
  });
  const before = await page.locator("#build").textContent();
  await request.get("/control?save");
  await expect(page.locator("#build")).not.toHaveText(before);
  await expect(page.getByLabel("Name")).toHaveValue("unfinished title");
  await expect(page.getByLabel("Name")).toBeFocused();
  await expect(page.getByLabel("Notes")).toHaveValue("unfinished notes");
  await expect(page.locator("#checkbox")).toBeChecked();
  await expect(page.locator("#radio")).toBeChecked();
  await expect(page.locator("#choice")).toHaveValues(["two", "three"]);
  expect(await page.evaluate(() => {
    const input = document.getElementById("name");
    return [input.defaultValue, input.selectionStart, input.selectionEnd, input.selectionDirection, scrollY];
  })).toEqual(["", 2, 8, "backward", 600]);
  expect(await page.evaluate(() => sessionStorage.getItem("woge-development-refresh-v1"))).toBeNull();
});

test("incoming reset accepts server state and sensitive or unopted values never enter the handoff", async ({ page, request }) => {
  await page.goto("/");
  await expect(page.getByRole("status")).toHaveText("Ready");
  await page.getByLabel("Name").fill("discard me");
  await page.locator("#password").fill("private-password");
  await page.locator("#private").fill("private-text");
  await page.locator("#file").setInputFiles({ name: "private.txt", mimeType: "text/plain", buffer: Buffer.from("private") });
  await page.evaluate(async () => {
    const { saveRefreshState } = await import("http://127.0.0.1:4274/refresh-state.js");
    saveRefreshState({ session: "test", renderedBuild: "1", generation: "1" });
    window.savedState = sessionStorage.getItem("woge-development-refresh-v1");
    document.getElementById("name").focus();
  });
  const saved = await page.evaluate(() => JSON.parse(window.savedState));
  expect(saved.controls.map((control) => control.key)).toEqual(["key:name"]);
  for (const value of ["private-password", "private-text", "private.txt"]) {
    expect(JSON.stringify(saved)).not.toContain(value);
  }
  const before = await page.locator("#build").textContent();
  await request.get("/control?reset");
  await expect(page.locator("#build")).not.toHaveText(before);
  await expect(page.getByLabel("Name")).toHaveValue("");
  await expect(page.getByLabel("Name")).not.toBeFocused();
  await expect(page.locator("#password")).toHaveValue("");
  expect(await page.locator("#file").evaluate((input) => input.files.length)).toBe(0);
  await request.get("/control?save");
});

test("unavailable storage still performs exactly one correct refresh", async ({ page, request }) => {
  await page.addInitScript(() => {
    Storage.prototype.getItem = Storage.prototype.setItem = Storage.prototype.removeItem = () => {
      throw new DOMException("Storage unavailable", "SecurityError");
    };
  });
  await page.goto("/");
  await expect(page.getByRole("status")).toHaveText("Ready");
  const before = await page.locator("#build").textContent();
  await page.getByLabel("Name").fill("not persisted");
  await request.get("/control?save");
  await expect(page.locator("#build")).not.toHaveText(before);
  await expect(page.getByRole("status")).toHaveText("Ready");
  await expect(page.getByLabel("Name")).toHaveValue("");
});

test("navigation, other sessions and malformed state cannot replay dirty values", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("status")).toHaveText("Ready");
  await page.evaluate(() => sessionStorage.setItem("woge-development-refresh-v1", "{broken"));
  await page.reload();
  await expect(page.getByRole("status")).toHaveText("Ready");
  expect(await page.evaluate(() => sessionStorage.getItem("woge-development-refresh-v1"))).toBeNull();
  await page.evaluate(async () => {
    const { saveRefreshState } = await import("http://127.0.0.1:4274/refresh-state.js");
    document.getElementById("name").value = "must not replay";
    const config = JSON.parse(document.querySelector('meta[name="woge-development"]').content);
    saveRefreshState({ session: "wrong-session", renderedBuild: config.build, generation: config.generation });
  });
  await page.reload();
  await expect(page.getByRole("status")).toHaveText("Ready");
  await expect(page.getByLabel("Name")).toHaveValue("");
  await page.evaluate(async () => {
    const { saveRefreshState } = await import("http://127.0.0.1:4274/refresh-state.js");
    document.getElementById("name").value = "different page";
    saveRefreshState({ session: "test", renderedBuild: "999", generation: "999" });
  });
  await page.goto("/another-page");
  await expect(page.getByRole("status")).toHaveText("Ready");
  await expect(page.getByLabel("Name")).toHaveValue("");
  expect(await page.evaluate(() => sessionStorage.getItem("woge-development-refresh-v1"))).toBeNull();
});

test("expired, mismatched and incompatible handoffs degrade without partial value restoration", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("status")).toHaveText("Ready");
  const result = await page.evaluate(async () => {
    const { saveRefreshState, restoreRefreshState } =
      await import("http://127.0.0.1:4274/refresh-state.js");
    const config = JSON.parse(document.querySelector('meta[name="woge-development"]').content);
    const next = { session: "test", renderedBuild: config.build, generation: config.generation };
    const input = document.getElementById("name");
    input.value = "edited";
    saveRefreshState(next);
    const state = JSON.parse(sessionStorage.getItem("woge-development-refresh-v1"));
    const notes = document.getElementById("notes");
    state.controls.push({
      key: "key:notes", localName: "input", type: "textarea", kind: "text", value: "another edit",
    });
    input.value = "";
    restoreRefreshState(state, { session: "test" });
    const unchanged = [input.value, notes.value];
    state.created = Date.now() - 60001;
    sessionStorage.setItem("woge-development-refresh-v1", JSON.stringify(state));
    return unchanged;
  });
  expect(result).toEqual(["", "Server notes"]);
  await page.reload();
  await expect(page.getByRole("status")).toHaveText("Ready");
  await expect(page.getByLabel("Name")).toHaveValue("");
  expect(await page.evaluate(() => sessionStorage.getItem("woge-development-refresh-v1"))).toBeNull();
  await page.evaluate(async () => {
    const { saveRefreshState } = await import("http://127.0.0.1:4274/refresh-state.js");
    const config = JSON.parse(document.querySelector('meta[name="woge-development"]').content);
    document.getElementById("name").value = "wrong generation";
    saveRefreshState({ session: "test", renderedBuild: config.build, generation: "999999" });
  });
  await page.reload();
  await expect(page.getByRole("status")).toHaveText("Ready");
  await expect(page.getByLabel("Name")).toHaveValue("");
  expect(await page.evaluate(() => sessionStorage.getItem("woge-development-refresh-v1"))).toBeNull();
});

test("handoff limits reject oversized dirty data and sensitive inherited autocomplete", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("status")).toHaveText("Ready");
  const saved = await page.evaluate(async () => {
    const { saveRefreshState } = await import("http://127.0.0.1:4274/refresh-state.js");
    const next = { session: "test", renderedBuild: "1", generation: "1" };
    const input = document.getElementById("name");
    input.value = "x".repeat(4096);
    saveRefreshState(next);
    const atLimit = JSON.parse(sessionStorage.getItem("woge-development-refresh-v1")).controls[0].value.length;
    input.value += "x";
    saveRefreshState(next);
    const oversized = sessionStorage.getItem("woge-development-refresh-v1");
    input.value = "secret";
    input.form.autocomplete = "off";
    saveRefreshState(next);
    const disabled = JSON.parse(sessionStorage.getItem("woge-development-refresh-v1"));
    input.form.removeAttribute("autocomplete");
    input.value = "";
    const controls = [];
    for (let index = 0; index < 200; index++) {
      const control = document.createElement("input");
      control.id = `bounded-${index}`;
      control.setAttribute("data-woge-development-preserve", "");
      control.value = "dirty";
      document.body.append(control);
      controls.push(control);
    }
    saveRefreshState(next);
    const atCount = JSON.parse(sessionStorage.getItem("woge-development-refresh-v1")).controls.length;
    const extra = controls[0].cloneNode();
    extra.id = "bounded-extra";
    extra.value = "dirty";
    document.body.append(extra);
    saveRefreshState(next);
    const tooMany = sessionStorage.getItem("woge-development-refresh-v1");
    extra.remove();
    for (const control of controls) control.value = "x".repeat(4096);
    saveRefreshState(next);
    const tooLarge = sessionStorage.getItem("woge-development-refresh-v1");
    return { atLimit, oversized, disabled, atCount, tooMany, tooLarge };
  });
  expect(saved.atLimit).toBe(4096);
  expect(saved.oversized).toBeNull();
  expect(saved.disabled.controls).toEqual([]);
  expect(saved.atCount).toBe(200);
  expect(saved.tooMany).toBeNull();
  expect(saved.tooLarge).toBeNull();
});

test("rapid saves settle on one newest ready document", async ({ page, request }) => {
  await page.goto("/");
  await expect(page.locator("#woge-development-status")).toBeVisible();
  const before = await page.locator("#build").textContent();
  await Promise.all(Array.from({ length: 8 }, () => request.get("/control?save")));
  await expect(page.locator("#build")).not.toHaveText(before);
  await expect(page.getByRole("status")).toHaveText("Ready");
});

test("native EventSource reconnect resumes current state after an offline edit", async ({ page, context, request }) => {
  await page.goto("/");
  await expect(page.getByRole("status")).toHaveText("Ready");
  const before = await page.locator("#build").textContent();
  await context.setOffline(true);
  await request.get("/control?save");
  await page.waitForTimeout(500);
  await context.setOffline(false);
  await expect(page.locator("#build")).not.toHaveText(before, { timeout: 10000 });
  await expect(page.getByRole("status")).toHaveText("Ready");
});

test("the overlay is dismissible by keyboard without stealing form focus", async ({ page, request }) => {
  await page.goto("/");
  await page.getByLabel("Name").focus();
  await request.get("/control?fail");
  await expect(page.getByRole("status")).toContainText("Build failed");
  await expect(page.getByLabel("Name")).toBeFocused();
  await page.getByRole("button", { name: "Hide development status" }).focus();
  await page.keyboard.press("Enter");
  await expect(page.locator("#woge-development-status")).toBeHidden();
  await request.get("/control?save");
});

test("stale, duplicate and out-of-order identities cannot trigger a document reload", async ({ page }) => {
  await page.addInitScript(() => {
    class ControlledEventSource extends EventTarget {
      constructor() { super(); window.testSource = this; }
      close() {}
    }
    window.EventSource = ControlledEventSource;
  });
  await page.goto("/");
  await page.waitForFunction(() => window.testSource);
  await page.evaluate(async () => {
    const { connectDevelopmentClient } = await import("http://127.0.0.1:4274/client.js");
    window.reloads = 0;
    connectDevelopmentClient({
      endpoint: "http://127.0.0.1:4274/events", build: "10", generation: "10", overlay: false,
    }, { reload: () => { window.reloads++; } });
    const emit = (sequence, build, generation) => window.testSource.dispatchEvent(
      new MessageEvent("snapshot", { data: JSON.stringify({
        version: 1, session: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
        sequence, build, renderedBuild: build, documentBuild: build, generation, phase: "READY", diagnostics: [],
      }) }),
    );
    emit("20", "10", "10");
    emit("19", "11", "11");
    emit("20", "11", "11");
    emit("21", "9", "11");
    emit("21", "11", "9");
    emit("21", "11", "11");
    emit("22", "12", "12");
  });
  expect(await page.evaluate(() => window.reloads)).toBe(1);
});

const color = (page, selector) => page.locator(selector).evaluate((element) => getComputedStyle(element).color);

test("stylesheet saves update every tab in place and keep page state", async ({ page, context, request }) => {
  await page.goto("/");
  await expect(page.getByRole("status")).toHaveText("Ready");
  const second = await context.newPage();
  await second.goto("http://127.0.0.1:4273/");
  await expect(second.getByRole("status")).toHaveText("Ready");
  await page.getByLabel("Name").fill("unsaved");
  await page.evaluate(() => {
    window.sameDocument = true;
    document.getElementById("name").focus();
    window.scrollTo(0, 600);
  });
  await request.get("/control?css=rgb(255,%200,%200)");
  await expect.poll(() => color(page, "h1")).toBe("rgb(255, 0, 0)");
  await expect.poll(() => color(page, "#build")).toBe("rgb(255, 0, 0)");
  await expect.poll(() => color(second, "h1")).toBe("rgb(255, 0, 0)");
  expect(await page.evaluate(() => [window.sameDocument, document.activeElement.id, scrollY])).toEqual([true, "name", 600]);
  await expect(page.getByLabel("Name")).toHaveValue("unsaved");
  await expect(page.locator('link[rel="stylesheet"][href*="/styles/"]')).toHaveCount(1);

  for (const blue of [10, 20, 30, 40, 255]) await request.get(`/control?css=rgb(0,%200,%20${blue})`);
  await expect.poll(() => color(page, "h1")).toBe("rgb(0, 0, 255)");
  await expect(page.locator('link[rel="stylesheet"][href*="/styles/"]')).toHaveCount(1);
  expect(await page.evaluate(() => window.sameDocument)).toBe(true);
  await second.close();
});

test("a stylesheet that fails to load falls back to one document refresh", async ({ page, request }) => {
  await page.goto("/");
  await expect(page.getByRole("status")).toHaveText("Ready");
  await page.evaluate(() => { window.sameDocument = true; });
  await request.get("/control?css-broken");
  await expect.poll(() => page.evaluate(() => window.sameDocument === undefined)).toBe(true);
  await request.get("/control?css=rgb(0,%20128,%200)");
  await expect.poll(() => color(page, "h1")).toBe("rgb(0, 128, 0)");
});

test("a stylesheet save after a Kotlin save keeps the document refresh", async ({ page, request }) => {
  await page.goto("/");
  await expect(page.getByRole("status")).toHaveText("Ready");
  const before = await page.locator("#build").textContent();
  await page.evaluate(() => { window.sameDocument = true; });
  await request.get("/control?save");
  await request.get("/control?css=rgb(0,%200,%20128)");
  await expect.poll(() => color(page, "h1")).toBe("rgb(0, 0, 128)");
  await expect(page.locator("#build")).not.toHaveText(before);
  expect(await page.evaluate(() => window.sameDocument)).toBeUndefined();
});

test("stylesheet updates are measured from save to applied style", async ({ page, request, browserName }) => {
  await page.goto("/");
  await expect(page.getByRole("status")).toHaveText("Ready");
  const samples = [];
  for (let index = 1; index <= 20; index += 1) {
    const value = `rgb(${index}, 1, 1)`;
    const started = Date.now();
    await request.get(`/control?css=${encodeURIComponent(value)}`);
    await page.waitForFunction((expected) => getComputedStyle(document.querySelector("h1")).color === expected, value, { polling: 5 });
    samples.push(Date.now() - started);
  }
  samples.sort((a, b) => a - b);
  const p50 = samples[Math.floor(samples.length * 0.5)];
  const p95 = samples[Math.ceil(samples.length * 0.95) - 1];
  test.info().annotations.push({ type: "css-hot-update", description: `${browserName} p50=${p50}ms p95=${p95}ms` });
  console.log(`css-hot-update ${browserName} p50=${p50}ms p95=${p95}ms`);
  expect(p95).toBeLessThan(2000);
});

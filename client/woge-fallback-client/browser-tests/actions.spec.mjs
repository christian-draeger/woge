import { expect, test } from "@playwright/test";

const mediaType = "application/vnd.woge.patch-stream; version=1";
test.beforeEach(async ({ page }) => {
  await page.goto("/action-forms");
  await page.waitForFunction(() => globalThis.actions !== undefined);
});

test("preserves successful controls and submitter overrides, keeps focus and announces once", async ({ page }) => {
  const requests = [];
  await page.route("**/preview", async (route) => {
    requests.push(route.request());
    await route.continue();
  });
  const preview = page.getByRole("button", { name: "Preview" });
  const response = page.waitForResponse("**/preview");
  await preview.focus();
  await preview.press("Enter");
  expect((await response).headers()["content-type"]).toBe(mediaType);
  await expect(page.locator("form")).not.toHaveAttribute("aria-busy");
  expect(await page.evaluate(() => actionErrors)).toEqual([]);
  await expect(page.getByRole("status")).toHaveText("Saved");
  expect(requests).toHaveLength(1);
  expect(requests[0].method()).toBe("POST");
  expect(requests[0].headers().accept).toBe(mediaType);
  expect(Array.from(new URLSearchParams(requests[0].postData()))).toEqual([
    ["title", "A & B"], ["tag", "one"], ["tag", "two"], ["_csrf", "token"], ["intent", "preview"],
  ]);
  await expect(page.getByRole("button", { name: "Preview" })).toBeFocused();
  await expect(page.locator("form")).not.toHaveAttribute("aria-busy");
  await expect(page.getByRole("button", { name: "Preview" })).not.toHaveAttribute("aria-disabled");
  await expect(page.getByRole("alert")).toBeEmpty();
  expect(await page.evaluate(() => actionErrors)).toEqual([]);
  await expect(page.locator('[role="status"]')).toHaveCount(1);
});

test("busy state is focusable, blocks duplicate submits and restores original attributes", async ({ page }) => {
  let release;
  let started;
  const received = new Promise((resolve) => { started = resolve; });
  const hold = new Promise((resolve) => { release = resolve; });
  let count = 0;
  await page.route("**/action", async (route) => {
    count++;
    started();
    await hold;
    await route.continue();
  });
  await page.locator("form").evaluate((form) => form.setAttribute("aria-busy", "false"));
  const button = page.getByRole("button", { name: "Save", exact: true });
  await button.focus();
  await button.press("Enter");
  await received;
  await expect(button).toBeFocused();
  await expect(button).toHaveAttribute("aria-disabled", "true");
  expect(await button.evaluate((element) => element.disabled)).toBe(false);
  await expect(page.locator("form")).toHaveAttribute("aria-busy", "true");
  await page.locator("form").evaluate((form) => form.requestSubmit(form.querySelector("button")));
  expect(count).toBe(1);
  release();
  await expect(page.getByRole("status")).toHaveText("Saved");
  await expect(page.locator("form")).toHaveAttribute("aria-busy", "false");
});

test("validation focuses the document-owned summary without a success or failure announcement", async ({ page }) => {
  await page.locator("form").evaluate((form) => form.action = "/action?validation");
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page.locator("#errors")).toBeFocused();
  await expect(page.locator("#errors a")).toHaveAttribute("href", "#title");
  await expect(page.locator('input[name="title"]')).toHaveValue("A & B");
  await expect(page.getByRole("alert")).toBeEmpty();
  await expect(page.getByRole("status")).toBeEmpty();
  await expect(page.locator("form")).not.toHaveAttribute("aria-busy");
  expect(await page.evaluate(() => actionErrors)).toEqual([]);
});

test("validation cannot redirect focus to an unconfigured element", async ({ page }) => {
  await page.locator("form").evaluate((form) => form.action = "/action?validation&summary=title");
  const button = page.getByRole("button", { name: "Save", exact: true });
  await button.focus();
  await button.press("Enter");
  await expect(page.getByRole("alert")).not.toBeEmpty();
  await expect(button).toBeFocused();
  await expect(page.locator("#errors")).toHaveCount(0);
});

test("stale validation never focuses or announces its summary", async ({ page }) => {
  await page.locator("form").evaluate((form) => form.action = "/action?validation&stale");
  const button = page.getByRole("button", { name: "Save", exact: true });
  await button.focus();
  await button.press("Enter");
  await expect(page.locator("form")).not.toHaveAttribute("aria-busy");
  await expect(button).toBeFocused();
  await expect(page.locator("#errors")).toHaveCount(0);
  await expect(page.getByRole("alert")).toBeEmpty();
});

test("a hidden or live validation summary is rejected rather than silently losing focus", async ({ page }) => {
  for (const attribute of ["hidden", "aria-live"]) {
    await page.reload();
    await page.waitForFunction(() => globalThis.actions !== undefined);
    await page.evaluate((attribute) => {
      document.querySelector("form").action = "/action?validation";
      document.addEventListener(Woge.AFTER_REPLACE_EVENT, () => {
        document.getElementById("errors")?.setAttribute(attribute, attribute === "hidden" ? "" : "polite");
      }, { once: true });
    }, attribute);
    await page.getByRole("button", { name: "Save", exact: true }).focus();
    await page.getByRole("button", { name: "Save", exact: true }).press("Enter");
    await expect(page.getByRole("alert")).not.toBeEmpty();
    await expect(page.getByRole("button", { name: "Save", exact: true })).toBeFocused();
    expect(await page.evaluate(() => actionErrors)).toEqual(["WOGE_ACTION_RESPONSE_REJECTED"]);
  }
});

test("network failure never replays a POST and leaves input editable with one alert", async ({ page }) => {
  let count = 0;
  await page.route("**/action", (route) => {
    count++;
    return route.abort("failed");
  });
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page.getByRole("alert")).toHaveText("The result is unknown. Check before submitting again.");
  expect(count).toBe(1);
  await expect(page.locator('input[name="title"]')).toHaveValue("A & B");
  await expect(page.locator("form")).not.toHaveAttribute("aria-busy");
  expect(await page.evaluate(() => actionErrors)).toEqual(["WOGE_NETWORK"]);
  await expect(page.getByRole("status")).toBeEmpty();
});

test("dispose cancels an owned request without retry and restores busy state", async ({ page }) => {
  await page.route("**/action", (route) => route.abort("failed"));
  await page.evaluate(() => {
    document.querySelector("form").requestSubmit(document.querySelector("button"));
    actions.dispose();
  });
  // Cancellation may happen before the browser sends anything; either case must not replay.
  await expect(page.locator("form")).not.toHaveAttribute("aria-busy");
  await expect(page.getByRole("alert")).toBeEmpty();
});

test("server-requested navigation is a same-origin GET, never a second POST", async ({ page }) => {
  const requests = [];
  await page.route("**/action", async (route) => {
    requests.push(route.request().method());
    await route.fulfill({ status: 200, headers: { "Woge-Navigate": "/complete" }, body: "" });
  });
  await page.route("**/complete", async (route) => {
    requests.push(route.request().method());
    await route.fulfill({ contentType: "text/html", body: "<h1>Complete</h1>" });
  });
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page).toHaveURL(/\/complete$/);
  expect(requests).toEqual(["POST", "GET"]);
});

for (const response of [
  { status: 200, contentType: "text/html", body: "<p>HTML is not a patch stream</p>" },
  { status: 403, contentType: mediaType, body: "" },
  { status: 200, headers: { "Woge-Navigate": "https://example.invalid/" }, body: "" },
  { status: 200, contentType: mediaType, body: "truncated" },
]) {
  test(`rejects incompatible response ${JSON.stringify(response)} without mutation replay`, async ({ page }) => {
    let count = 0;
    await page.route("**/action", async (route) => {
      count++;
      await route.fulfill(response);
    });
    await page.getByRole("button", { name: "Save", exact: true }).click();
    await expect(page.getByRole("alert")).not.toBeEmpty();
    expect(count).toBe(1);
    await expect(page).toHaveURL(/\/action-forms$/);
    await expect(page.locator("form")).not.toHaveAttribute("aria-busy");
  });
}

for (const attributes of [
  { "data-woge-action": null },
  { method: "get" },
  { enctype: "multipart/form-data" },
  { enctype: "text/plain" },
  { target: "_blank" },
  { action: "https://example.invalid/action" },
  { "data-woge-alert": "missing" },
  { "accept-charset": "iso-8859-1" },
  { action: "http://[" },
]) {
  test(`leaves unsupported form native ${JSON.stringify(attributes)}`, async ({ page }) => {
    const prevented = await page.locator("form").evaluate((form, attributes) => {
      for (const [name, value] of Object.entries(attributes)) {
        if (value === null) form.removeAttribute(name);
        else form.setAttribute(name, value);
      }
      const event = new SubmitEvent("submit", { bubbles: true, cancelable: true, submitter: form.querySelector("button") });
      form.dispatchEvent(event);
      return event.defaultPrevented;
    }, attributes);
    expect(prevented).toBe(false);
  });
}

test("browser validation still prevents an invalid submission", async ({ page }) => {
  await page.locator('input[name="title"]').evaluate((input) => { input.required = true; input.value = ""; });
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page.locator("form")).not.toHaveAttribute("aria-busy");
  await expect(page.getByRole("alert")).toBeEmpty();
});

test("FormData events, line endings and controls named action use browser semantics", async ({ page }) => {
  let body;
  await page.route("**/action", async (route) => {
    body = new URLSearchParams(route.request().postData());
    await route.continue();
  });
  await page.locator("form").evaluate((form) => {
    form.insertAdjacentHTML("beforeend", '<input name="action" value="user text"><textarea name="lines">one\ntwo</textarea>');
    form.addEventListener("formdata", (event) => event.formData.append("application", "included"));
    form.requestSubmit();
  });
  await expect(page.getByRole("status")).toHaveText("Saved");
  expect(body.get("action")).toBe("user text");
  expect(body.get("lines")).toBe("one\r\ntwo");
  expect(body.get("application")).toBe("included");
  expect(body.has("intent")).toBe(false);
});

test("file controls and image submitters remain native", async ({ page }) => {
  for (const control of ['<input name="upload" type="file">', '<input name="image" type="image" src="/image.png">']) {
    const prevented = await page.locator("form").evaluate((form, markup) => {
      const control = document.createElement("div");
      control.innerHTML = markup;
      form.append(control);
      const input = control.firstElementChild;
      const event = new SubmitEvent("submit", {
        bubbles: true, cancelable: true, submitter: input.type === "image" ? input : form.querySelector("button"),
      });
      form.dispatchEvent(event);
      control.remove();
      return event.defaultPrevented;
    }, control);
    expect(prevented).toBe(false);
  }
});

test("stale interaction response remains silent", async ({ page }) => {
  await page.route("**/action", async (route) => {
    await route.continue({ url: `${route.request().url()}?stale=1` });
  });
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page.locator("form")).not.toHaveAttribute("aria-busy");
  await expect(page.getByRole("alert")).toBeEmpty();
  await expect(page.getByRole("status")).toBeEmpty();
  expect(await page.evaluate(() => actionErrors)).toEqual([]);
});

test("with JavaScript disabled the marked form submits normally", async ({ browser }) => {
  const context = await browser.newContext({ javaScriptEnabled: false });
  try {
    const page = await context.newPage();
    await page.route("**/action", async (route) => {
      expect(route.request().method()).toBe("POST");
      expect(new URLSearchParams(route.request().postData()).get("intent")).toBe("save");
      expect(route.request().headers().accept).not.toBe(mediaType);
      await route.fulfill({ contentType: "text/html", body: "<h1>Native result</h1>" });
    });
    await page.goto("http://127.0.0.1:4173/action-forms");
    await page.getByRole("button", { name: "Save", exact: true }).click();
    await expect(page.getByRole("heading")).toHaveText("Native result");
  } finally {
    await context.close();
  }
});

import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { chromium } from "@playwright/test";

const origin = process.argv[2];
assert.ok(origin, "The adapter TCK must provide its server origin");

const browser = await chromium.launch();
try {
  let expectedMutations = 4; // The HTTP contract performed native/enhanced navigation and region updates.
  for (const javaScriptEnabled of [false, true]) {
    const context = await browser.newContext({
      javaScriptEnabled,
      extraHTTPHeaders: { "X-Tck-Subject": "tck-user" },
    });
    try {
      const page = await context.newPage();
      const complete = `${origin}/woge-tck/action-complete`;
      await page.goto(complete);
      const form = page.locator("form");
      assert.equal(await form.getAttribute("method"), "post");
      assert.equal(await form.getAttribute("enctype"), "application/x-www-form-urlencoded");
      assert.equal(await form.getAttribute("accept-charset"), "UTF-8");
      assert.equal(await form.getAttribute("action"), "/woge-actions/tck-submit");

      await page.getByRole("textbox", { name: "Command value" }).fill("update");
      const [response] = await Promise.all([
        page.waitForNavigation(),
        page.getByRole("button", { name: "Submit command" }).click(),
      ]);
      assert.equal(response.status(), 200);
      assert.equal(page.url(), complete);
      assert.equal(response.request().method(), "GET");
      assert.equal(response.request().redirectedFrom().method(), "POST");
      assert.equal((await response.request().redirectedFrom().response()).status(), 303);
      expectedMutations++;
      assert.ok((await page.textContent("body")).includes(`Completed mutations: ${expectedMutations}`));
      await page.reload();
      assert.ok((await page.textContent("body")).includes(`Completed mutations: ${expectedMutations}`));

      // A duplicate scalar is submitted using two ordinary named controls, not a JS-only request.
      await page.goto(complete);
      await page.getByRole("textbox", { name: "Command value" }).fill("<private>");
      const [invalid] = await Promise.all([
        page.waitForNavigation(),
        page.getByRole("button", { name: "Submit ambiguous command" }).click(),
      ]);
      assert.equal(invalid.status(), 400);
      assert.equal(await page.getByRole("textbox", { name: "Command value" }).inputValue(), "<private>");
      assert.ok((await page.textContent("body")).includes("value: REPEATED"));
      assert.equal(await page.locator("private").count(), 0);
      await page.goto(complete);
      assert.ok((await page.textContent("body")).includes(`Completed mutations: ${expectedMutations}`));

      await page.getByRole("textbox", { name: "Command value" }).fill("denied");
      const [denied] = await Promise.all([
        page.waitForNavigation(),
        page.getByRole("button", { name: "Submit command" }).click(),
      ]);
      assert.equal(denied.status(), 403);
      await page.goto(complete);
      assert.ok((await page.textContent("body")).includes(`Completed mutations: ${expectedMutations}`));

      await context.setExtraHTTPHeaders({ "X-Tck-Subject": "other-user" });
      await page.getByRole("textbox", { name: "Command value" }).fill("accepted");
      const [unauthorized] = await Promise.all([
        page.waitForNavigation(),
        page.getByRole("button", { name: "Submit command" }).click(),
      ]);
      assert.equal(unauthorized.status(), 403);
      await page.goto(complete);
      assert.ok((await page.textContent("body")).includes(`Completed mutations: ${expectedMutations}`));
    } finally {
      await context.close();
    }
  }
  await verifyEnhanced(browser, expectedMutations);
} finally {
  await browser.close();
}

async function verifyEnhanced(browser, expectedMutations) {
  const context = await browser.newContext({ extraHTTPHeaders: { "X-Tck-Subject": "tck-user" } });
  try {
    const source = await readFile(new URL("../dist/woge-fallback.js", import.meta.url), "utf8");
    await context.addInitScript((source) => {
      addEventListener("DOMContentLoaded", async () => {
        const url = URL.createObjectURL(new Blob([source], { type: "text/javascript" }));
        try {
          const client = await import(url);
          globalThis.tckActions = client.installWogeActionForms(document, client.createWogePatchRuntime(document));
        } finally {
          URL.revokeObjectURL(url);
        }
      }, { once: true });
    }, source);
    const page = await context.newPage();
    const complete = `${origin}/woge-tck/action-complete`;
    await page.goto(complete);
    await page.waitForFunction(() => globalThis.tckActions !== undefined);
    await page.getByRole("textbox", { name: "Command value" }).fill("accepted");
    const posted = page.waitForResponse((response) => response.request().method() === "POST");
    const navigated = page.waitForNavigation();
    await page.getByRole("button", { name: "Submit command", exact: true }).click();
    const response = await posted;
    assert.equal(response.status(), 200);
    assert.equal(response.headers()["woge-navigate"], "/woge-tck/action-complete");
    const navigation = await navigated;
    assert.equal(navigation.request().method(), "GET");
    assert.equal(navigation.request().redirectedFrom(), null);
    assert.equal(page.url(), complete);
    await page.waitForFunction(() => globalThis.tckActions !== undefined);
    expectedMutations++;
    assert.ok((await page.textContent("body")).includes(`Completed mutations: ${expectedMutations}`));
    await page.reload();
    assert.ok((await page.textContent("body")).includes(`Completed mutations: ${expectedMutations}`));
    await page.waitForFunction(() => globalThis.tckActions !== undefined);

    await page.getByRole("textbox", { name: "Command value" }).fill("update");
    const updated = page.waitForResponse((response) => response.request().method() === "POST");
    await page.getByRole("button", { name: "Submit command", exact: true }).focus();
    await page.getByRole("button", { name: "Submit command", exact: true }).press("Enter");
    const patchResponse = await updated;
    assert.equal(patchResponse.status(), 200);
    assert.equal(patchResponse.headers()["content-type"].replaceAll(" ", ""),
      "application/vnd.woge.patch-stream;version=1");
    expectedMutations++;
    await page.waitForFunction((count) =>
      document.querySelector("section")?.textContent === `Completed mutations: ${count}` &&
      document.getElementById("tck-action-status")?.textContent === "Saved",
    expectedMutations);
    assert.equal(await page.locator("form").getAttribute("aria-busy"), null);
    assert.equal(await page.getByRole("button", { name: "Submit command", exact: true }).evaluate(
      (button) => document.activeElement === button,
    ), true);
    assert.equal(page.url(), complete);
    await page.reload();
    assert.ok((await page.textContent("body")).includes(`Completed mutations: ${expectedMutations}`));
    await page.waitForFunction(() => globalThis.tckActions !== undefined);

    for (const [value, button, status] of [
      ["<private>", "Submit ambiguous command", 400],
      ["denied", "Submit command", 403],
    ]) {
      await page.getByRole("textbox", { name: "Command value" }).fill(value);
      const posted = page.waitForResponse((response) => response.request().method() === "POST");
      await page.getByRole("button", { name: button, exact: true }).click();
      assert.equal((await posted).status(), status);
      if (status === 400) {
        await page.waitForFunction(() => document.activeElement?.id === "tck-error-summary");
        assert.equal(await page.getByRole("alert").textContent(), "");
        assert.equal(await page.getByRole("status").textContent(), "");
        assert.equal(await page.getByRole("textbox", { name: "Command value" }).getAttribute("aria-describedby"),
          "tck-command-value-error");
        assert.equal(await page.locator("#tck-error-summary a").getAttribute("href"), "#tck-command-value");
      } else {
        await page.getByRole("alert").waitFor({ state: "visible" });
      }
      assert.equal(page.url(), complete);
      assert.equal(await page.getByRole("textbox", { name: "Command value" }).inputValue(), value);
      assert.ok((await page.textContent("body")).includes(`Completed mutations: ${expectedMutations}`));
      await page.reload();
      await page.waitForFunction(() => globalThis.tckActions !== undefined);
    }
    await context.setExtraHTTPHeaders({ "X-Tck-Subject": "other-user" });
    await page.getByRole("textbox", { name: "Command value" }).fill("accepted");
    const unauthorized = page.waitForResponse((response) => response.request().method() === "POST");
    await page.getByRole("button", { name: "Submit command", exact: true }).click();
    assert.equal((await unauthorized).status(), 403);
    await page.getByRole("alert").waitFor({ state: "visible" });
    assert.ok((await page.textContent("body")).includes(`Completed mutations: ${expectedMutations}`));
  } finally {
    await context.close();
  }
}

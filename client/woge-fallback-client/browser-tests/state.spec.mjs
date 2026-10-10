import { expect, test } from "@playwright/test";
import { completeFrame, encodeStream, patchFrame } from "../test-support/protocol-fixture.mjs";

test.beforeEach(async ({ page }) => {
  await page.goto("/");
  await page.waitForFunction(() => globalThis.Woge !== undefined);
});

test("preserves a keyed dirty value, keyboard focus and selection while updating server defaults", async ({ page }) => {
  await setup(page, '<input data-woge-state-key="title" value="Original">');
  const input = page.locator("input");
  await input.fill("My draft");
  await input.evaluate((element) => element.setSelectionRange(2, 5, "backward"));
  expect(await apply(page, '<input data-woge-state-key="title" value="Server">')).toBeNull();
  await expect(input).toHaveValue("My draft");
  await expect(input).toBeFocused();
  expect(await input.evaluate((element) =>
    [element.selectionStart, element.selectionEnd, element.selectionDirection, element.defaultValue]))
    .toEqual([2, 5, "backward", "Server"]);
});

test("clean controls accept server values and deliberate reset clears dirty state", async ({ page }) => {
  await setup(page, '<input data-woge-state-key="title" value="Original">');
  expect(await apply(page, '<input data-woge-state-key="title" value="Server">')).toBeNull();
  await expect(page.locator("input")).toHaveValue("Server");
  await page.locator("input").fill("Draft");
  expect(await apply(page, '<input data-woge-state-key="title" data-woge-state="reset" value="Reset">', 1)).toBeNull();
  await expect(page.locator("input")).toHaveValue("Reset");
});

test("dirty unkeyed or incompatible controls fail before any DOM or revision change", async ({ page }) => {
  await setup(page, '<input value="Original">');
  await page.locator("input").fill("Draft");
  expect(await apply(page, "<p>Discard</p>")).toBe("WOGE_BROWSER_STATE_CONFLICT");
  await expect(page.locator("input")).toHaveValue("Draft");
  await expect(page.locator("main")).toHaveAttribute("data-woge-revision", "0");
  await page.locator("main").evaluate((element) => element.setAttribute("data-woge-state", "reset"));
  expect(await apply(page, "<p>Reset everything</p>")).toBeNull();
});

test("checkboxes and multiple selections preserve browser-owned values", async ({ page }) => {
  const html = '<input type="checkbox" data-woge-state-key="flag">' +
    '<select multiple data-woge-state-key="tags"><option value="one">One</option><option value="two">Two</option></select>';
  await setup(page, html);
  await page.locator("input").check();
  await page.locator("select").selectOption(["one", "two"]);
  expect(await apply(page, html)).toBeNull();
  await expect(page.locator("input")).toBeChecked();
  await expect(page.locator("select")).toHaveValues(["one", "two"]);
});

test("selected file inputs retain their native node without copying FileList values", async ({ page }) => {
  const html = '<input type="file" data-woge-state-key="upload">';
  await setup(page, html);
  await page.locator("input").setInputFiles({ name: "draft.txt", mimeType: "text/plain", buffer: Buffer.from("Draft") });
  await page.locator("input").evaluate((element) => { globalThis.originalInput = element; });
  expect(await apply(page, html)).toBeNull();
  expect(await page.locator("input").evaluate((element) =>
    [element === originalInput, element.files[0].name])).toEqual([true, "draft.txt"]);
});

test("local islands preserve their native subtree and reject nested Woge region ownership", async ({ page }) => {
  const html = '<div data-woge-state-key="editor" data-woge-island><div contenteditable>Original</div></div>';
  await setup(page, html);
  await page.locator("[contenteditable]").fill("Draft");
  await page.locator("[contenteditable]").focus();
  expect(await apply(page, html)).toBeNull();
  await expect(page.locator("[contenteditable]")).toHaveText("Draft");
  await expect(page.locator("[contenteditable]")).toBeFocused();
  await setup(page, '<div data-woge-island data-woge-state-key="editor">' +
    '<section data-woge-region="nested" data-woge-revision="0">Nested</section></div>');
  expect(await apply(page, '<div data-woge-island data-woge-state-key="editor">Placeholder</div>'))
    .toBe("WOGE_BROWSER_STATE_CONFLICT");
  await expect(page.locator('[data-woge-region="nested"]')).toHaveText("Nested");
});

test("missing dirty select options and duplicate state keys fail closed", async ({ page }) => {
  await setup(page, '<select data-woge-state-key="choice"><option>A</option><option>B</option></select>');
  await page.locator("select").selectOption({ label: "B" });
  expect(await apply(page, '<select data-woge-state-key="choice"><option>A</option></select>'))
    .toBe("WOGE_BROWSER_STATE_CONFLICT");
  expect(await apply(page, '<input data-woge-state-key="same"><input data-woge-state-key="same">'))
    .toBe("WOGE_INVALID_BROWSER_STATE");
  await expect(page.locator("select")).toHaveValue("B");
});

test("radio group state survives and unsupported contenteditable requires explicit ownership", async ({ page }) => {
  const html = '<input type="radio" name="choice" data-woge-state-key="a" checked>' +
    '<input type="radio" name="choice" data-woge-state-key="b">';
  await setup(page, html);
  await page.locator('[data-woge-state-key="b"]').check();
  expect(await apply(page, html)).toBeNull();
  await expect(page.locator('[data-woge-state-key="b"]')).toBeChecked();
  await expect(page.locator('[data-woge-state-key="a"]')).not.toBeChecked();
  await setup(page, '<div contenteditable>Draft</div>');
  expect(await apply(page, "<p>Gone</p>")).toBe("WOGE_BROWSER_STATE_CONFLICT");
});

test("a stable open dialog region retains keyed focus and dirty values during replacement", async ({ page }) => {
  await page.evaluate(() => {
    document.querySelector("main").outerHTML =
      '<dialog data-woge-region="summary-1" data-woge-revision="0">' +
      '<input data-woge-state-key="query" role="combobox" aria-expanded="false" value="Initial"></dialog>';
    document.querySelector("dialog").showModal();
    globalThis.runtime = Woge.createWogePatchRuntime(document);
  });
  await page.locator("input").fill("Draft");
  expect(await apply(page, '<input data-woge-state-key="query" role="combobox" aria-expanded="false" value="Server">'))
    .toBeNull();
  await expect(page.locator("dialog")).toHaveAttribute("open");
  await expect(page.getByRole("combobox")).toHaveValue("Draft");
  await expect(page.getByRole("combobox")).toBeFocused();
});

test("table edits survive concurrent unrelated region updates and a stable popover stays open", async ({ page }) => {
  await setup(page, '<table><tbody><tr><td><input data-woge-state-key="cell" value="Original"></td></tr></tbody></table>');
  await page.evaluate(() => {
    const sibling = document.createElement("section");
    sibling.setAttribute("data-woge-region", "sibling");
    sibling.setAttribute("data-woge-revision", "0");
    document.body.append(sibling);
    globalThis.runtime = Woge.createWogePatchRuntime(document);
  });
  await page.locator("input").fill("Draft");
  const bytes = encodeStream([patchFrame({ target: "sibling", html: "<p>Other update</p>" }), completeFrame()]);
  await page.evaluate(async (bytes) => runtime.applyPatchStream(byteStream(Uint8Array.from(bytes))), Array.from(bytes));
  await expect(page.locator("input")).toBeFocused();
  expect(await apply(page, '<table><tbody><tr><td><input data-woge-state-key="cell" value="Server"></td></tr></tbody></table>'))
    .toBeNull();
  await expect(page.locator("input")).toHaveValue("Draft");
  await page.evaluate(() => {
    const main = document.querySelector("main");
    main.setAttribute("popover", "manual");
    main.showPopover();
  });
  expect(await apply(page, '<input data-woge-state-key="cell" value="Updated">', 1)).toBeNull();
  expect(await page.locator("main").evaluate((element) => element.matches(":popover-open"))).toBe(true);
});

test("keyed focus fallback is explicit and snapshot plans cannot be committed twice", async ({ page }) => {
  await setup(page, '<button data-woge-state-key="action">Action</button>');
  await page.getByRole("button").focus();
  expect(await apply(page, "<p>No button</p>")).toBeNull();
  await expect(page.locator("main")).toBeFocused();
  const code = await page.evaluate(() => {
    const snapshot = Woge.captureWogeBrowserState(document.querySelector("main"));
    const plan = Woge.prepareWogeBrowserState(snapshot, document.createDocumentFragment());
    plan.commit();
    try { plan.commit(); } catch (problem) { return problem.code; }
  });
  expect(code).toBe("WOGE_INVALID_BROWSER_STATE");
});

test("native files inside islands are retained and an explicit file reset discards selection", async ({ page }) => {
  const island = '<div data-woge-island data-woge-state-key="island">' +
    '<input type="file" data-woge-state-key="file"></div>';
  await setup(page, island);
  await page.locator("input").setInputFiles({ name: "draft.txt", mimeType: "text/plain", buffer: Buffer.from("Draft") });
  expect(await apply(page, island)).toBeNull();
  expect(await page.locator("input").evaluate((element) => element.files[0].name)).toBe("draft.txt");
  await setup(page, '<input type="file" data-woge-state-key="file">');
  await page.locator("input").setInputFiles({ name: "draft.txt", mimeType: "text/plain", buffer: Buffer.from("Draft") });
  expect(await apply(page, '<input type="file" data-woge-state-key="file" data-woge-state="reset">')).toBeNull();
  expect(await page.locator("input").evaluate((element) => element.files.length)).toBe(0);
  expect(await page.evaluate(() => {
    const main = document.querySelector("main");
    try {
      Woge.prepareWogeBrowserState(Woge.captureWogeBrowserState(main), main);
    } catch (problem) { return problem.code; }
  })).toBe("WOGE_INVALID_BROWSER_STATE");
});

async function setup(page, html) {
  await page.evaluate((html) => {
    const main = document.querySelector("main");
    main.innerHTML = html;
    main.setAttribute("data-woge-revision", "0");
    main.tabIndex = -1;
    globalThis.runtime = Woge.createWogePatchRuntime(document);
  }, html);
}

async function apply(page, html, baseRevision = 0) {
  const bytes = encodeStream([patchFrame({ html, baseRevision, nextRevision: baseRevision + 1 }), completeFrame()]);
  return page.evaluate(async (bytes) => {
    try {
      await runtime.applyPatchStream(byteStream(Uint8Array.from(bytes)));
      return null;
    } catch (problem) {
      return problem.code;
    }
  }, Array.from(bytes));
}

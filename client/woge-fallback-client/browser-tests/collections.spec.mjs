import { expect, test } from "@playwright/test";
import { completeFrame, encodeStream, patchFrame, readGoldenStream } from "../test-support/protocol-fixture.mjs";

test.beforeEach(async ({ page }) => {
  await page.goto("/");
  await page.waitForFunction(() => globalThis.Woge !== undefined);
  await page.evaluate(() => {
    const old = document.querySelector("main");
    const collection = document.createElement("ul");
    collection.setAttribute("data-woge-region", "summary-1");
    collection.setAttribute("data-woge-revision", "0");
    collection.tabIndex = -1;
    old.replaceWith(collection);
    globalThis.runtime = Woge.createWogePatchRuntime(document);
  });
});

test("applies the shared Replace Append Remove golden from one-byte chunks", async ({ page }) => {
  const result = await apply(page, await readGoldenStream("collection-patch-stream-v1"), "one-byte");
  expect(result).toBeNull();
  await expect(page.locator("ul")).toBeEmpty();
  await expect(page.locator("ul")).toHaveAttribute("data-woge-revision", "3");
});

test("append deduplicates by direct-child item ID while continuing collection revisions", async ({ page }) => {
  const first = append({ html: '<li data-woge-item="item-1"><button>First</button></li>' });
  expect(await apply(page, stream(first))).toBeNull();
  await page.getByRole("button", { name: "First" }).focus();
  const duplicate = append({ baseRevision: 1, nextRevision: 2,
    html: '<li data-woge-item="item-1"><button>Changed</button></li>' });
  expect(await apply(page, stream(duplicate))).toBeNull();
  await expect(page.locator('[data-woge-item="item-1"]')).toHaveCount(1);
  await expect(page.getByRole("button", { name: "First" })).toBeFocused();
  await expect(page.locator("ul")).toHaveAttribute("data-woge-revision", "2");
});

test("remove focuses its declared region only when the removed item owned focus and is idempotent", async ({ page }) => {
  expect(await apply(page, stream(append()))).toBeNull();
  await page.getByRole("button", { name: "First" }).focus();
  expect(await apply(page, stream(remove({ baseRevision: 1, nextRevision: 2 })))).toBeNull();
  await expect(page.locator("ul")).toBeFocused();
  expect(await apply(page, stream(remove({ baseRevision: 2, nextRevision: 3 })))).toBeNull();
  await expect(page.locator("ul")).toHaveAttribute("data-woge-revision", "3");
  await expect(page.locator("ul")).toBeFocused();
});

test("unfocused removal preserves focus outside the item and unregisters nested regions", async ({ page }) => {
  await page.evaluate(() => {
    const button = document.createElement("button");
    button.textContent = "Outside";
    document.body.append(button);
  });
  const nested = '<li data-woge-item="item-1"><section data-woge-region="nested" data-woge-revision="0">Nested</section></li>';
  expect(await apply(page, stream(append({ html: nested })))).toBeNull();
  await page.getByRole("button", { name: "Outside" }).focus();
  expect(await apply(page, stream(remove({ baseRevision: 1, nextRevision: 2 })))).toBeNull();
  await expect(page.getByRole("button", { name: "Outside" })).toBeFocused();
  expect(await apply(page, stream(patchFrame({ target: "nested" })))).toBe("WOGE_UNKNOWN_TARGET");
});

test("malformed or active append payloads and duplicate nested targets fail without mutation", async ({ page }) => {
  for (const [html, code] of [
    ['<li data-woge-item="other">Wrong identity</li>', "WOGE_INVALID_ITEM"],
    ['<li data-woge-item="item-1">One</li><li>Two</li>', "WOGE_INVALID_ITEM"],
    ['<li data-woge-item="item-1"><script>bad()</script></li>', "WOGE_ACTIVE_CONTENT"],
    ['<li data-woge-item="item-1"><section data-woge-region="summary-1" data-woge-revision="0"></section></li>',
      "WOGE_DUPLICATE_TARGET"],
  ]) {
    expect(await apply(page, stream(append({ html })))).toBe(code);
    await expect(page.locator("ul")).toBeEmpty();
    await expect(page.locator("ul")).toHaveAttribute("data-woge-revision", "0");
  }
});

test("removal with no usable focus fallback leaves the item and revision intact", async ({ page }) => {
  expect(await apply(page, stream(append()))).toBeNull();
  await page.locator("ul").evaluate((element) => element.removeAttribute("tabindex"));
  await page.getByRole("button", { name: "First" }).focus();
  expect(await apply(page, stream(remove({ baseRevision: 1, nextRevision: 2 })))).toBe("WOGE_INVALID_ITEM");
  await expect(page.getByRole("button", { name: "First" })).toBeFocused();
  await expect(page.locator("ul")).toHaveAttribute("data-woge-revision", "1");
});

test("collection lifecycle events run only for real mutations and stale frames are ignored", async ({ page }) => {
  await page.evaluate(() => {
    globalThis.events = [];
    for (const name of ["before-append", "after-append", "before-remove", "after-remove"]) {
      document.addEventListener(`woge:${name}`, (event) => events.push([name, event.detail.itemId]));
    }
  });
  expect(await apply(page, stream(append()))).toBeNull();
  expect(await apply(page, stream(append()))).toBeNull();
  expect(await apply(page, stream(append({ baseRevision: 1, nextRevision: 2 })))).toBeNull();
  expect(await apply(page, stream(remove({ baseRevision: 2, nextRevision: 3 })))).toBeNull();
  expect(await apply(page, stream(remove({ baseRevision: 3, nextRevision: 4 })))).toBeNull();
  expect(await page.evaluate(() => events)).toEqual([
    ["before-append", "item-1"], ["after-append", "item-1"],
    ["before-remove", "item-1"], ["after-remove", "item-1"],
  ]);
});

test("append and remove reject lifecycle changes before Woge mutates the collection", async ({ page }) => {
  await page.evaluate(() => {
    document.querySelector("ul").addEventListener("woge:before-append", (event) => {
      event.target.setAttribute("data-woge-revision", "9");
    }, { once: true });
  });
  expect(await apply(page, stream(append()))).toBe("WOGE_REGION_STATE_CHANGED");
  await expect(page.locator("ul")).toBeEmpty();
  await page.locator("ul").evaluate((element) => element.setAttribute("data-woge-revision", "0"));
  expect(await apply(page, stream(append()))).toBeNull();
  await page.evaluate(() => {
    document.querySelector("ul").addEventListener("woge:before-remove", () => {
      document.querySelector("[data-woge-item]").setAttribute("data-woge-item", "changed");
    }, { once: true });
  });
  expect(await apply(page, stream(remove({ baseRevision: 1, nextRevision: 2 })))).toBe("WOGE_ITEM_CHANGED");
  await expect(page.getByRole("button", { name: "First" })).toHaveCount(1);
  await expect(page.locator("ul")).toHaveAttribute("data-woge-revision", "1");
});

test("an item root registers its region and cannot be its own removal focus fallback", async ({ page }) => {
  const html = '<li data-woge-item="item-1" data-woge-region="nested" data-woge-revision="0" tabindex="-1">First</li>';
  expect(await apply(page, stream(append({ html })))).toBeNull();
  expect(await apply(page, stream(patchFrame({ target: "nested", html: "<button>Nested</button>" })))).toBeNull();
  expect(await apply(page, stream(remove({ baseRevision: 1, nextRevision: 2, focusTarget: "nested" }))))
    .toBe("WOGE_INVALID_ITEM");
  await expect(page.locator("ul")).toHaveAttribute("data-woge-revision", "1");
  expect(await apply(page, stream(remove({ baseRevision: 1, nextRevision: 2 })))).toBeNull();
  expect(await apply(page, stream(patchFrame({ target: "nested", baseRevision: 1, nextRevision: 2 }))))
    .toBe("WOGE_UNKNOWN_TARGET");
});

test("removing an open dialog item closes its overlay and recovers active focus", async ({ page }) => {
  const html = '<dialog data-woge-item="item-1"><button>First</button></dialog>';
  expect(await apply(page, stream(append({ html })))).toBeNull();
  await page.locator("dialog").evaluate((element) => element.showModal());
  await expect(page.getByRole("button", { name: "First" })).toBeFocused();
  expect(await apply(page, stream(remove({ baseRevision: 1, nextRevision: 2 })))).toBeNull();
  await expect(page.locator("dialog")).toHaveCount(0);
  await expect(page.locator("ul")).toBeFocused();
});

test("normal table rows survive inert parsing and append to the declared table body", async ({ page }) => {
  await page.evaluate(() => {
    document.querySelector("ul").outerHTML =
      '<table><tbody data-woge-region="summary-1" data-woge-revision="0" tabindex="-1"></tbody></table>';
    globalThis.runtime = Woge.createWogePatchRuntime(document);
  });
  expect(await apply(page, stream(append({ html: '<tr data-woge-item="item-1"><td>Cell</td></tr>' })))).toBeNull();
  await expect(page.locator("tbody > tr > td")).toHaveText("Cell");
});

function append(overrides = {}) {
  return patchFrame({ operation: "append", itemId: "item-1",
    html: '<li data-woge-item="item-1"><button>First</button></li>', ...overrides });
}

function remove(overrides = {}) {
  return patchFrame({ operation: "remove", itemId: "item-1", focusTarget: "summary-1", html: "", ...overrides });
}

function stream(frame) {
  return encodeStream([frame, completeFrame()]);
}

async function apply(page, bytes, splitAt) {
  return page.evaluate(async ({ bytes, splitAt }) => {
    try {
      await runtime.applyPatchStream(byteStream(Uint8Array.from(bytes), splitAt));
      return null;
    } catch (problem) {
      return problem.code;
    }
  }, { bytes: Array.from(bytes), splitAt });
}

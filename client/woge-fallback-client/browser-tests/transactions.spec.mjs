import { expect, test } from "@playwright/test";
import { completeFrame, encodeStream, patchFrame, rawFrame, transactionFrame }
  from "../test-support/protocol-fixture.mjs";

const replacement = '<input id="draft" data-woge-state-key="draft" value="Server">';
test.beforeEach(async ({ page }) => {
  await page.goto("/");
  await page.waitForFunction(() => globalThis.Woge !== undefined);
  await page.evaluate(() => {
    document.head.innerHTML = '<meta name="woge-page-epoch" content="epoch-a">';
    document.body.innerHTML = '<button id="outside">Outside</button>' +
      '<main tabindex="-1" data-woge-region="alpha" data-woge-revision="0">' +
      '<input id="draft" data-woge-state-key="draft" value="Original"></main>' +
      '<section data-woge-region="beta" data-woge-revision="0">Original beta</section>';
    globalThis.runtime = Woge.createWogePatchRuntime(document);
    globalThis.lifecycle = [];
    for (const phase of ["before", "after"]) document.addEventListener(`woge:${phase}-replace`, (event) => {
      lifecycle.push([phase, event.target.getAttribute("data-woge-region"),
        document.querySelector('[data-woge-region="beta"]').textContent]);
    });
  });
});

function members(second = {}) {
  return [patchFrame({ target: "alpha", html: replacement }),
    patchFrame({ target: "beta", html: "Updated beta", ...second })];
}
async function apply(page, patches = members(), motion = false) {
  const bytes = encodeStream([transactionFrame(patches, motion), completeFrame()]);
  return page.evaluate(async (values) => {
    try { return { completion: await runtime.applyPatchStream(byteStream(Uint8Array.from(values), "one-byte")) }; }
    catch (problem) { return { failure: Woge.classifyWogeFailure(problem) }; }
  }, Array.from(bytes));
}

for (const [name, second] of [
  ["unknown target", { target: "missing" }],
  ["forward revision gap", { baseRevision: 1, nextRevision: 2 }],
  ["active HTML", { html: '<script>throw new Error("must never run")</script>' }],
]) test(`invalid ${name} applies nothing, preserves focus and reports browser failure`, async ({ page }) => {
  await page.locator("#draft").focus();
  expect(await apply(page, members(second))).toEqual({ failure: { code: "WOGE_TRANSACTION_REJECTED",
    category: "browser-apply", outcome: "fail-closed" } });
  await expect(page.locator("#draft")).toHaveValue("Original");
  await expect(page.locator("#draft")).toBeFocused();
  await expect(page.locator('[data-woge-region="beta"]')).toHaveText("Original beta");
  await expect(page.locator('[data-woge-region="alpha"]')).toHaveAttribute("data-woge-revision", "0");
  await expect(page.locator('[data-woge-region="beta"]')).toHaveAttribute("data-woge-revision", "0");
});

test("dirty state conflict on the last member rejects all replacements", async ({ page }) => {
  await page.locator('[data-woge-region="beta"]').evaluate((element) => {
    element.innerHTML = '<input id="last" value="Original">';
  });
  await page.locator("#last").fill("Unsaved");
  expect((await apply(page)).failure.code).toBe("WOGE_TRANSACTION_REJECTED");
  await expect(page.locator("#draft")).toHaveValue("Original");
  await expect(page.locator("#last")).toHaveValue("Unsaved");
});

for (const mode of ["unsupported", "reduced", "opt-out", "animated"]) {
  test(`${mode} path has equivalent DOM, revision, lifecycle, keyed focus and selection`, async ({ page }) => {
    if (mode === "reduced") await page.emulateMedia({ reducedMotion: "reduce" });
    await page.evaluate((mode) => {
      globalThis.transitionCalls = 0;
      const native = document.startViewTransition?.bind(document);
      document.startViewTransition = mode === "unsupported" ? undefined : (callback) => {
        transitionCalls++;
        if (mode !== "animated") throw new Error("Motion path must be bypassed");
        return native(callback);
      };
    }, mode);
    await page.locator("#draft").fill("Unsaved draft");
    await page.locator("#draft").evaluate((element) => element.setSelectionRange(2, 6, "backward"));
    expect(await apply(page, members(), mode !== "opt-out")).toEqual({ completion: { patchCount: 1 } });
    await expect(page.locator("#draft")).toHaveValue("Unsaved draft");
    await expect(page.locator("#draft")).toBeFocused();
    expect(await page.locator("#draft").evaluate((element) =>
      [element.selectionStart, element.selectionEnd, element.selectionDirection, element.defaultValue]))
      .toEqual([2, 6, "backward", "Server"]);
    await expect(page.locator('[data-woge-region="beta"]')).toHaveText("Updated beta");
    for (const target of ["alpha", "beta"]) {
      await expect(page.locator(`[data-woge-region="${target}"]`)).toHaveAttribute("data-woge-revision", "1");
    }
    expect(await page.evaluate(() => transitionCalls)).toBe(mode === "animated" ? 1 : 0);
    expect(await page.evaluate(() => lifecycle)).toEqual([
      ["before", "alpha", "Original beta"], ["before", "beta", "Original beta"],
      ["after", "alpha", "Updated beta"], ["after", "beta", "Updated beta"],
    ]);
  });
}

test("external focus and deliberate lifecycle focus movement are never stolen", async ({ page }) => {
  await page.locator("#draft").focus();
  await page.evaluate(() => document.addEventListener("woge:before-replace", () =>
    document.querySelector("#outside").focus(), { once: true }));
  expect(await apply(page)).toEqual({ completion: { patchCount: 1 } });
  await expect(page.locator("#outside")).toBeFocused();
});

test("overlapping targets and cross-member duplicate nested regions fail before mutation", async ({ page }) => {
  const nested = '<section data-woge-region="child" data-woge-revision="0">Child</section>';
  expect((await apply(page, [patchFrame({ target: "alpha", html: nested }),
    patchFrame({ target: "beta", html: nested })])).failure.code).toBe("WOGE_TRANSACTION_REJECTED");
  await page.locator('[data-woge-region="alpha"]').evaluate((element) => {
    element.insertAdjacentHTML("beforeend", '<section data-woge-region="child" data-woge-revision="0">Child</section>');
    globalThis.runtime = Woge.createWogePatchRuntime(document);
  });
  expect((await apply(page, [patchFrame({ target: "alpha", html: replacement }),
    patchFrame({ target: "child" })])).failure.code).toBe("WOGE_TRANSACTION_REJECTED");
  await expect(page.locator('[data-woge-region="child"]')).toHaveText("Child");
  await expect(page.locator("#draft")).toHaveValue("Original");
});

test("a stale member ignores the complete transaction without lifecycle or partial updates", async ({ page }) => {
  await page.evaluate(() => runtime.beginInteraction(["alpha"]));
  expect(await apply(page)).toEqual({ completion: { patchCount: 1, stalePatchCount: 1 } });
  await expect(page.locator('[data-woge-region="beta"]')).toHaveText("Original beta");
  expect(await page.evaluate(() => lifecycle)).toEqual([]);
});

test("unexpected focus failure is a visible commit failure, never a successful partial transaction", async ({ page }) => {
  await page.locator("#draft").focus();
  expect((await apply(page, [patchFrame({ target: "alpha", html: '<div data-woge-state-key="draft">Not focusable</div>' }),
    patchFrame({ target: "beta", html: "Updated beta" })])).failure.code).toBe("WOGE_TRANSACTION_APPLY_FAILED");
  await expect(page.locator('[data-woge-region="beta"]')).toHaveText("Updated beta");
});

test("truncated transaction payload never applies its already decoded first member", async ({ page }) => {
  const payload = encodeStream(members());
  const bytes = encodeStream([rawFrame(4, "application/json; charset=utf-8", '{"viewTransition":false}', payload), completeFrame()]);
  const result = await page.evaluate(async (values) => {
    try { await runtime.applyPatchStream(byteStream(Uint8Array.from(values))); }
    catch (problem) { return problem.code; }
  }, Array.from(bytes));
  expect(result).toBe("WOGE_MISSING_TERMINAL");
  await expect(page.locator("#draft")).toHaveValue("Original");
});

test("synchronous custom-element mutation cannot silently detach a later transaction target", async ({ page }) => {
  await page.locator('[data-woge-region="alpha"]').evaluate((element) => {
    customElements.define("transaction-disconnect", class extends HTMLElement {
      disconnectedCallback() { document.querySelector('[data-woge-region="beta"]')?.remove(); }
    });
    element.insertAdjacentHTML("beforeend", "<transaction-disconnect></transaction-disconnect>");
  });
  expect((await apply(page)).failure.code).toBe("WOGE_TRANSACTION_APPLY_FAILED");
});

test("ordinary patches do not load the separately delivered transaction module", async ({ page }) => {
  const modules = [];
  page.on("request", (request) => { if (request.url().endsWith("/transactions.js")) modules.push(request.url()); });
  const bytes = encodeStream([patchFrame({ target: "alpha", html: replacement }), completeFrame()]);
  await page.evaluate(async (values) => runtime.applyPatchStream(byteStream(Uint8Array.from(values))), Array.from(bytes));
  expect(modules).toEqual([]);
  expect(await apply(page, [patchFrame({ target: "alpha", html: replacement, baseRevision: 1, nextRevision: 2 }),
    patchFrame({ target: "beta", html: "Updated beta" })])).toEqual({ completion: { patchCount: 1 } });
  expect(modules).toHaveLength(1);
});

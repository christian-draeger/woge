import { expect, test } from "@playwright/test";
import { completeFrame, encodeStream, patchFrame } from "../test-support/protocol-fixture.mjs";

// ADR 0048: the runtime never announces, never moves focus and never adds busy state on its own.
const announcementState = () => ({
  activeId: document.activeElement?.id ?? null,
  liveRegions: document.querySelectorAll('[aria-live], [role="status"], [role="alert"], [role="log"]').length,
  busy: document.querySelectorAll("[aria-busy]").length,
});

test("deferred loading and completion stay silent and keep focus", async ({ page }) => {
  await page.goto("/deferred");
  await page.evaluate(() => {
    const button = Object.assign(document.createElement("button"), { id: "outside", type: "button", textContent: "Outside" });
    document.body.prepend(button);
    button.focus();
  });
  await page.waitForFunction(() => globalThis.deferredStarted === true);
  await expect(page.locator('[data-woge-region="fast"]')).toHaveText("Fast result");

  expect(await page.evaluate(announcementState)).toEqual({ activeId: "outside", liveRegions: 0, busy: 0 });

  await page.evaluate(async () =>
    fetch(`/release-deferred?run=${encodeURIComponent(globalThis.deferredRunId)}`, { method: "POST" }),
  );
  await page.waitForFunction(() => globalThis.deferredCompletion?.patchCount === 2);

  expect(await page.evaluate(announcementState)).toEqual({ activeId: "outside", liveRegions: 0, busy: 0 });
});

test("a superseded patch is ignored without content change, focus move or announcement", async ({ page }) => {
  await page.goto("/");
  await page.waitForFunction(() => globalThis.Woge !== undefined);
  const stale = encodeStream([patchFrame({ interactionSequence: 1 }), completeFrame()]);

  const result = await page.evaluate(async (values) => {
    document.body.innerHTML =
      '<button id="outside" type="button">Outside</button>' +
      '<main data-woge-region="summary-1" data-woge-revision="0"><p>Original</p></main>';
    document.getElementById("outside").focus();
    let failure = null;
    try {
      await globalThis.Woge.createWogePatchRuntime(document).applyPatchStream(
        globalThis.byteStream(Uint8Array.from(values)),
      );
    } catch (problem) {
      failure = problem;
    }
    const classified = globalThis.Woge.classifyWogeFailure(failure);
    return {
      code: classified.code,
      outcome: classified.outcome,
      html: document.querySelector("main").innerHTML,
      activeId: document.activeElement?.id ?? null,
      liveRegions: document.querySelectorAll('[aria-live], [role="status"], [role="alert"], [role="log"]').length,
    };
  }, Array.from(stale));

  expect(result).toEqual({
    code: "WOGE_INTERACTION_MISMATCH",
    outcome: "ignore-stale",
    html: "<p>Original</p>",
    activeId: "outside",
    liveRegions: 0,
  });
});

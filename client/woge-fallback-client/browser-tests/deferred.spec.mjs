import { expect, test } from "@playwright/test";

test("applies the first completed region before the deferred stream finishes", async ({ page }) => {
  await page.goto("/deferred");
  await page.waitForFunction(() => globalThis.deferredStarted === true);

  await expect(page.locator('[data-woge-region="fast"]')).toHaveText("Fast result");
  await expect(page.locator('[data-woge-region="slow"]')).toHaveText("Loading slow");

  const beforeRelease = await page.evaluate(() => ({
    completion: globalThis.deferredCompletion ?? null,
    order: globalThis.deferredTimeline.applied.map(({ target }) => target),
  }));
  expect(beforeRelease).toEqual({ completion: null, order: ["fast"] });

  const releaseStatus = await page.evaluate(async () =>
    fetch(`/release-deferred?run=${encodeURIComponent(globalThis.deferredRunId)}`, { method: "POST" })
      .then((response) => response.status),
  );
  expect(releaseStatus).toBe(204);

  await page.waitForFunction(() => globalThis.deferredCompletion?.patchCount === 2);
  await expect(page.locator('[data-woge-region="slow"]')).toHaveText("Slow result");

  const timeline = await page.evaluate(() => globalThis.deferredTimeline);
  expect(timeline.applied.map(({ target }) => target)).toEqual(["fast", "slow"]);
  expect(timeline.shellParsed).toBeLessThanOrEqual(timeline.applied[0].at);
  expect(timeline.applied[0].at).toBeLessThanOrEqual(timeline.applied[1].at);
  expect(timeline.applied[1].at).toBeLessThanOrEqual(timeline.completed);
});

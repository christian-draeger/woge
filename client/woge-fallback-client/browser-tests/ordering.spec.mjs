import { expect, test } from "@playwright/test";
import { completeFrame, encodeStream, patchFrame } from "../test-support/protocol-fixture.mjs";

test.beforeEach(async ({ page }) => {
  await page.goto("/");
  await page.waitForFunction(() => globalThis.Woge !== undefined);
});

test("latest search intent wins even when older work completes last from the same revision", async ({ page }) => {
  const result = await page.evaluate(async () => {
    const observations = [];
    const runtime = Woge.createWogePatchRuntime(document, { observer: (event) => observations.push(event) });
    const old = runtime.beginInteraction(["summary-1"]);
    const current = runtime.beginInteraction(["summary-1"]);
    return { old, current, frozen: Object.isFrozen(current) && Object.isFrozen(current.targets[0]) };
  });
  expect(result.old).toEqual({
    pageEpoch: "epoch-a", interactionSequence: "1", targets: [{ target: "summary-1", baseRevision: "0" }],
  });
  expect(result.current.interactionSequence).toBe("2");
  expect(result.frozen).toBe(true);
  const results = await page.evaluate(async ({ newest, oldest }) => {
    const events = [];
    const runtime = Woge.createWogePatchRuntime(document, { observer: (event) => events.push(event) });
    const stream = (bytes) => new ReadableStream({
      start(controller) { controller.enqueue(Uint8Array.from(bytes)); controller.close(); },
    });
    await runtime.applyPatchStream(stream(newest));
    const completion = await runtime.applyPatchStream(stream(oldest));
    return { completion, stale: events.filter((event) => event.phase === "finished" && event.outcome === "stale") };
  }, {
    newest: Array.from(encodeStream([patchFrame({ interactionSequence: 2, html: "<p>kotlin</p>" }), completeFrame()])),
    oldest: Array.from(encodeStream([patchFrame({ interactionSequence: 1, html: "<script>old work</script>" }), completeFrame()])),
  });
  await expect(page.locator("main")).toHaveText("kotlin");
  expect(results.completion.patchCount).toBe(1);
  expect(results.stale).toHaveLength(1);
  expect(results.stale[0].code).toBe("WOGE_STALE_PATCH");
});

test("invalid multi-target intent leaves every target unchanged and sequences cannot wrap", async ({ page }) => {
  const result = await page.evaluate(() => {
    const runtime = Woge.createWogePatchRuntime(document);
    const errors = [];
    for (const targets of [[], ["summary-1", "summary-1"], ["summary-1", "missing"]]) {
      try { runtime.beginInteraction(targets); } catch (problem) { errors.push(problem.code); }
    }
    const initial = document.querySelector("main").getAttribute("data-woge-interaction-sequence");
    const first = runtime.beginInteraction(["summary-1"]);
    document.querySelector("main").setAttribute("data-woge-interaction-sequence", "9223372036854775807");
    const exhausted = Woge.createWogePatchRuntime(document);
    try { exhausted.beginInteraction(["summary-1"]); } catch (problem) { errors.push(problem.code); }
    return { errors, initial, first, sequence: document.querySelector("main").dataset.wogeInteractionSequence };
  });
  expect(result.errors).toEqual([
    "WOGE_INVALID_INTERACTION", "WOGE_INVALID_INTERACTION", "WOGE_UNKNOWN_TARGET", "WOGE_INTERACTION_EXHAUSTED",
  ]);
  expect(result.initial).toBeNull();
  expect(result.first.interactionSequence).toBe("1");
  expect(result.sequence).toBe("9223372036854775807");
});

test("stale frames do not stop a stream's valid other-target updates or trigger lifecycle", async ({ page }) => {
  await page.evaluate(() => {
    document.body.insertAdjacentHTML("beforeend",
      '<aside data-woge-region="other" data-woge-revision="0">Other</aside>');
    window.events = [];
    document.addEventListener("woge:before-replace", (event) => events.push(event.detail.target));
    window.runtime = Woge.createWogePatchRuntime(document);
    runtime.beginInteraction(["summary-1"]);
  });
  const bytes = encodeStream([
    patchFrame({ html: "<p>Superseded deferred</p>" }),
    patchFrame({ target: "other", patchId: "patch-2", html: "<p>Current other</p>" }),
    completeFrame(2),
  ]);
  const completion = await page.evaluate(async (bytes) => runtime.applyPatchStream(new ReadableStream({
    start(controller) { controller.enqueue(Uint8Array.from(bytes)); controller.close(); },
  })), Array.from(bytes));
  expect(completion.patchCount).toBe(2);
  await expect(page.locator("main")).toHaveText("Original");
  await expect(page.locator("aside")).toHaveText("Current other");
  expect(await page.evaluate(() => events)).toEqual(["other"]);
});

test("region resynchronization uses one declared safe replacement and cannot retry a failed revision", async ({ page }) => {
  const result = await page.evaluate(async ({ first, wrongTarget }) => {
    const runtime = Woge.createWogePatchRuntime(document);
    const stream = (bytes) => new ReadableStream({
      start(controller) { controller.enqueue(Uint8Array.from(bytes)); controller.close(); },
    });
    const contexts = [];
    const completion = await runtime.refetchRegion("summary-1", async (context) => {
      contexts.push(context);
      return stream(first);
    });
    const errors = [];
    let loads = 0;
    try {
      await runtime.refetchRegion("summary-1", async () => { loads++; return stream(wrongTarget); });
    } catch (problem) { errors.push(problem.code); }
    try {
      await runtime.refetchRegion("summary-1", async () => { loads++; return stream(wrongTarget); });
    } catch (problem) { errors.push(problem.code); }
    return { completion, contexts, errors, loads };
  }, {
    first: Array.from(encodeStream([patchFrame({ interactionSequence: 1, html: "<p>Authoritative</p>" }), completeFrame()])),
    wrongTarget: Array.from(encodeStream([
      patchFrame({ target: "other", interactionSequence: 2, baseRevision: 1, nextRevision: 2 }),
      completeFrame(),
    ])),
  });
  expect(result.completion.patchCount).toBe(1);
  expect(result.contexts[0]).toEqual({
    pageEpoch: "epoch-a", interactionSequence: "1", targets: [{ target: "summary-1", baseRevision: "0" }],
  });
  expect(result.errors).toEqual(["WOGE_INVALID_RESYNC", "WOGE_RESYNC_EXHAUSTED"]);
  expect(result.loads).toBe(1);
  await expect(page.locator("main")).toHaveText("Authoritative");
  await expect(page.locator("main")).toHaveAttribute("data-woge-revision", "1");
});

test("server-requested refreshes are unbudgeted but keep the replacement contract", async ({ page }) => {
  const result = await page.evaluate(async ({ first, wrongTarget }) => {
    const runtime = Woge.createWogePatchRuntime(document);
    const stream = (bytes) => new ReadableStream({
      start(controller) { controller.enqueue(Uint8Array.from(bytes)); controller.close(); },
    });
    const errors = [];
    for (let attempt = 0; attempt < 2; attempt++) {
      try {
        await runtime.refreshRegion("summary-1", async () => { throw new Error("offline"); });
      } catch (problem) { errors.push(problem.message); }
    }
    try {
      await runtime.refreshRegion("summary-1", async () => stream(wrongTarget));
    } catch (problem) { errors.push(problem.code); }
    const completion = await runtime.refreshRegion("summary-1", async () => stream(first));
    return { errors, completion };
  }, {
    first: Array.from(encodeStream([patchFrame({ interactionSequence: 4, html: "<p>Live</p>" }), completeFrame()])),
    wrongTarget: Array.from(encodeStream([
      patchFrame({ target: "other", interactionSequence: 3, baseRevision: 0, nextRevision: 1 }),
      completeFrame(),
    ])),
  });
  expect(result.errors).toEqual(["offline", "offline", "WOGE_INVALID_RESYNC"]);
  expect(result.completion.patchCount).toBe(1);
  await expect(page.locator("main")).toHaveText("Live");
});

test("a recovery completed after newer intent is ignored and cancellation never invokes its loader", async ({ page }) => {
  const result = await page.evaluate(async (bytes) => {
    const events = [];
    const runtime = Woge.createWogePatchRuntime(document, { observer: (event) => events.push(event) });
    let release;
    const applying = runtime.refetchRegion("summary-1", () => new Promise((resolve) => { release = resolve; }));
    runtime.beginInteraction(["summary-1"]);
    release(new ReadableStream({
      start(controller) { controller.enqueue(Uint8Array.from(bytes)); controller.close(); },
    }));
    await applying;
    const controller = new AbortController();
    controller.abort();
    let invoked = false;
    let error;
    try {
      await runtime.refetchRegion("summary-1", async () => { invoked = true; }, { signal: controller.signal });
    } catch (problem) { error = problem.code; }
    return { invoked, error, stale: events.filter((event) => event.outcome === "stale").length };
  }, Array.from(encodeStream([
    patchFrame({ interactionSequence: 1, html: "<p>Old recovery</p>" }), completeFrame(),
  ])));
  expect(result).toEqual({ invoked: false, error: "WOGE_CANCELLED", stale: 1 });
  await expect(page.locator("main")).toHaveText("Original");
});

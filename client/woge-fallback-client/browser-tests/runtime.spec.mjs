import { expect, test } from "@playwright/test";
import {
  completeFrame,
  encodeStream,
  patchFrame,
  rawFrame,
  readGoldenStream,
} from "../test-support/protocol-fixture.mjs";

test.beforeEach(async ({ page }) => {
  await page.goto("/");
  await page.waitForFunction(() => globalThis.Woge !== undefined);
});

test("applies the shared JVM golden patch from one-byte chunks", async ({ page }) => {
  await resetPage(page, {
    regionAttributes: 'class="project-summary app-owned" data-woge-revision="7" data-woge-interaction-sequence="41"',
  });

  const result = await apply(page, await readGoldenStream(), "one-byte");

  expect(result.error).toBeNull();
  expect(result.html).toBe("<p>Tasks &lt;today&gt;</p>");
  expect(result.revision).toBe("8");
  expect(result.className).toBe("project-summary app-owned");
  expect(result.patchCount).toBe(1);
});

test("preserves modern HTML CSS hooks and registers nested regions", async ({ page }) => {
  await resetPage(page);
  const first = patchFrame({
    html:
      '<woge-card class="grid md:grid-cols-[1fr_auto]" style="container-type:inline-size;--accent:oklch(62% .2 250)" ' +
      'data-state="ready"><a href="/projects">Projects</a>' +
      '<section data-woge-region="nested-1" data-woge-revision="0"><p>Nested fallback</p></section></woge-card>',
  });
  const nested = patchFrame({
    patchId: "patch-2",
    target: "nested-1",
    html: '<p class="text-balance">Nested update</p>',
  });

  const result = await apply(page, encodeStream([first, nested, completeFrame(2)]), 17);

  expect(result.error).toBeNull();
  expect(result.html).toContain("md:grid-cols-[1fr_auto]");
  expect(result.html).toContain("--accent:oklch");
  expect(result.html).toContain("Nested update");
  expect(result.patchCount).toBe(2);
});

test("resolves targets only through the active page registry", async ({ page }) => {
  await resetPage(page);
  const original = "<p>Original</p>";

  const unknown = await apply(
    page,
    encodeStream([patchFrame({ target: "missing" }), completeFrame()]),
    "one-byte",
  );
  expect(unknown.error).toBe("WOGE_UNKNOWN_TARGET");
  expect(unknown.html).toBe(original);

  const malformedMetadata =
    '{"protocolVersion":1,"operation":"replace","patchId":"patch-1","epoch":"epoch-a",' +
    '"target":"[data-woge-region]","interactionSequence":0,"baseRevision":0,"nextRevision":1}';
  const malformed = await apply(
    page,
    encodeStream([rawFrame(1, "text/html; charset=utf-8", malformedMetadata, "<p>Bad</p>")]),
    13,
  );
  expect(malformed.error).toBe("WOGE_INVALID_METADATA");
  expect(malformed.html).toBe(original);

  await page.locator("body").evaluate((body) => {
    body.insertAdjacentHTML("beforeend", '<aside data-woge-region="summary-1" data-woge-revision="0"></aside>');
  });
  const duplicate = await apply(page, encodeStream([patchFrame(), completeFrame()]), 21);
  expect(duplicate.error).toBe("WOGE_DUPLICATE_TARGET");
  expect(duplicate.html).toBe(original);

  await page.evaluate(() => {
    document.body.innerHTML = '<main data-woge-region="[summary]" data-woge-revision="0"><p>Original</p></main>';
  });
  const invalid = await apply(page, encodeStream([patchFrame(), completeFrame()]), 9);
  expect(invalid.error).toBe("WOGE_INVALID_TARGET");
});

test("rejects executable content and leaves the existing DOM unchanged", async ({ page }) => {
  const hostile = [
    "<script>globalThis.patchScriptRan=true</script>",
    "<style>body{display:none}</style>",
    '<iframe srcdoc="<p>active</p>"></iframe>',
    '<img src="/missing" onerror="globalThis.patchScriptRan=true">',
    '<a href="java&#x73;cript:globalThis.patchScriptRan=true">bad</a>',
    '<a href="data:text/html,bad">bad</a>',
    '<a href="vbscript:bad">bad</a>',
    '<img srcset="/one.png 1x, /two.png 2x">',
    '<link rel="stylesheet" href="/patch.css">',
    '<meta http-equiv="refresh" content="0;url=/other">',
    '<template shadowrootmode="open"><script>globalThis.patchScriptRan=true</script></template>',
  ];

  for (const html of hostile) {
    await resetPage(page);
    const result = await apply(page, encodeStream([patchFrame({ html }), completeFrame()]), "one-byte");
    expect(result.error, html).toBe("WOGE_ACTIVE_CONTENT");
    expect(result.html, html).toBe("<p>Original</p>");
    expect(result.revision, html).toBe("0");
    expect(await page.evaluate(() => globalThis.patchScriptRan)).toBeUndefined();
  }
});

test("rejects duplicate incoming regions atomically", async ({ page }) => {
  await resetPage(page);
  const html =
    '<section data-woge-region="nested" data-woge-revision="0"></section>' +
    '<aside data-woge-region="nested" data-woge-revision="0"></aside>';

  const result = await apply(page, encodeStream([patchFrame({ html }), completeFrame()]), 37);

  expect(result.error).toBe("WOGE_DUPLICATE_TARGET");
  expect(result.html).toBe("<p>Original</p>");
  expect(result.revision).toBe("0");
});

test("emits delegated lifecycle events and closes removed native overlays", async ({ page }) => {
  await page.evaluate(() => {
    globalThis.lifecycle = [];
    globalThis.connections = [];
    customElements.define(
      "woge-probe",
      class extends HTMLElement {
        connectedCallback() {
          globalThis.connections.push(`connected:${this.id}`);
        }

        disconnectedCallback() {
          globalThis.connections.push(`disconnected:${this.id}`);
        }
      },
    );
    document.body.innerHTML =
      '<main class="stable target" data-woge-region="summary-1" data-woge-revision="0">' +
      '<dialog id="details" open>Details</dialog><div id="menu" popover="manual">Menu</div>' +
      '<woge-probe id="old"></woge-probe></main>';
    const popover = document.querySelector("#menu");
    if (typeof popover.showPopover === "function") popover.showPopover();
    document.addEventListener("woge:before-replace", (event) => {
      const activePopover = event.target.querySelector("#menu").matches(":popover-open");
      globalThis.lifecycle.push(
        `dispose:${event.detail.patchId}:${event.target.querySelector("dialog").open}:${activePopover}`,
      );
    });
    document.addEventListener("woge:after-replace", (event) => {
      globalThis.lifecycle.push(`mount:${event.detail.patchId}:${event.target.querySelector("#new") !== null}`);
      globalThis.lifecycle.push(`update:${event.target.dataset.wogeRevision}`);
    });
  });
  const result = await apply(
    page,
    encodeStream([patchFrame({ html: '<woge-probe id="new"></woge-probe>' }), completeFrame()]),
    19,
  );

  expect(result.error).toBeNull();
  expect(result.className).toBe("stable target");
  expect(await page.evaluate(() => globalThis.lifecycle)).toEqual([
    "dispose:patch-1:true:true",
    "mount:patch-1:true",
    "update:1",
  ]);
  expect(await page.evaluate(() => globalThis.connections)).toContain("disconnected:old");
  expect(await page.evaluate(() => globalThis.connections)).toContain("connected:new");
});

test("rejects epoch interaction and revision mismatches before mutation", async ({ page }) => {
  await resetPage(page, {
    regionAttributes: 'data-woge-revision="4" data-woge-interaction-sequence="9"',
  });
  const cases = [
    [patchFrame({ epoch: "epoch-old", interactionSequence: 9, baseRevision: 4, nextRevision: 5 }), "WOGE_STALE_PAGE_EPOCH"],
    [patchFrame({ interactionSequence: 8, baseRevision: 4, nextRevision: 5 }), "WOGE_INTERACTION_MISMATCH"],
    [patchFrame({ interactionSequence: 9, baseRevision: 3, nextRevision: 4 }), "WOGE_REVISION_MISMATCH"],
  ];

  for (const [frame, code] of cases) {
    const result = await apply(page, encodeStream([frame, completeFrame()]), "one-byte");
    expect(result.error).toBe(code);
    expect(result.html).toBe("<p>Original</p>");
    expect(result.revision).toBe("4");
  }
});

test("cancels and unlocks an unfinished readable stream", async ({ page }) => {
  const result = await page.evaluate(async () => {
    const runtime = globalThis.Woge.createWogePatchRuntime(document);
    const stream = new ReadableStream({ start() {} });
    const controller = new AbortController();
    const applying = runtime.applyPatchStream(stream, { signal: controller.signal });
    controller.abort("test cancellation");
    let error;
    try {
      await applying;
    } catch (problem) {
      error = problem.code ?? problem.name;
    }
    return { error, locked: stream.locked };
  });

  expect(result).toEqual({ error: "WOGE_CANCELLED", locked: false });
});

test("reports module and patch application timing", async ({ page }, testInfo) => {
  const regionCount = 20;
  await page.evaluate((count) => {
    document.body.innerHTML = Array.from(
      { length: count },
      (_, index) => `<section data-woge-region="region-${index}" data-woge-revision="0"><p>Old</p></section>`,
    ).join("");
  }, regionCount);
  const frames = Array.from({ length: regionCount }, (_, index) =>
    patchFrame({ patchId: `patch-${index}`, target: `region-${index}`, html: `<p>New ${index}</p>` }),
  );
  const bytes = encodeStream([...frames, completeFrame(regionCount)]);

  const timing = await page.evaluate(async (values) => {
    const runtime = globalThis.Woge.createWogePatchRuntime(document);
    const stream = globalThis.byteStream(Uint8Array.from(values), 4096);
    const started = performance.now();
    await runtime.applyPatchStream(stream);
    return {
      moduleLoadParseEvalMs: globalThis.wogeModuleLoadParseEvalMs,
      patchApplyMs: performance.now() - started,
    };
  }, Array.from(bytes));

  expect(timing.moduleLoadParseEvalMs).toBeGreaterThanOrEqual(0);
  expect(timing.patchApplyMs).toBeGreaterThanOrEqual(0);
  console.log(
    `[woge-metrics] browser=${testInfo.project.name} module_load_parse_eval_ms=${timing.moduleLoadParseEvalMs.toFixed(3)} ` +
      `patch_apply_20_ms=${timing.patchApplyMs.toFixed(3)}`,
  );
  await testInfo.attach("runtime-timing.json", {
    body: Buffer.from(`${JSON.stringify({ browser: testInfo.project.name, ...timing }, null, 2)}\n`),
    contentType: "application/json",
  });
});

async function resetPage(page, { regionAttributes = 'data-woge-revision="0"' } = {}) {
  await page.evaluate((attributes) => {
    document.querySelector('meta[name="woge-page-epoch"]').content = "epoch-a";
    document.body.innerHTML = `<main data-woge-region="summary-1" ${attributes}><p>Original</p></main>`;
    delete globalThis.patchScriptRan;
  }, regionAttributes);
}

async function apply(page, encoded, split) {
  return page.evaluate(
    async ({ values, splitAt }) => {
      const bytes = Uint8Array.from(values);
      const stream = globalThis.byteStream(bytes, splitAt);
      let error = null;
      let patchCount = null;
      try {
        const runtime = globalThis.Woge.createWogePatchRuntime(document);
        patchCount = (await runtime.applyPatchStream(stream)).patchCount;
      } catch (problem) {
        error = problem.code ?? problem.name;
      }
      const region = document.querySelector('[data-woge-region="summary-1"]');
      return {
        error,
        patchCount,
        html: region?.innerHTML ?? null,
        revision: region?.getAttribute("data-woge-revision") ?? null,
        className: region?.className ?? null,
      };
    },
    { values: Array.from(encoded), splitAt: split },
  );
}

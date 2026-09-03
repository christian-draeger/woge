import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { test, expect } from "@playwright/test";
import { completeFrame, encodeFrames, errorFrame, patchFrame } from "./protocol-fixture.js";

const runtimePath = fileURLToPath(new URL("../runtime.js", import.meta.url));

test.beforeEach(async ({ page }) => {
  await page.setContent(`
    <!doctype html>
    <html>
      <head><meta name="woge-page-epoch" content="epoch-a"></head>
      <body>
        <main data-woge-region="summary-1" data-woge-revision="0"><p>Original</p></main>
      </body>
    </html>
  `);
  await page.addScriptTag({ content: await readFile(runtimePath, "utf8") });
});

test("applies a replace patch from one-byte chunks", async ({ page }) => {
  const encoded = encodeFrames([
    patchFrame({ html: '<section class="grid md:grid-cols-2"><woge-card data-state="ready">🐺 Updated</woge-card></section>' }),
    completeFrame()
  ]);

  const result = await apply(page, encoded, "one-byte");

  expect(result.error).toBeNull();
  expect(result.html).toContain("<woge-card");
  expect(result.html).toContain("🐺 Updated");
  expect(result.revision).toBe("1");
});

test("does not depend on one network chunk per frame", async ({ page }) => {
  const encoded = encodeFrames([patchFrame({ html: "<p>Split safely</p>" }), completeFrame()]);

  for (const split of [0, 1, 4, 5, 9, 17, Math.floor(encoded.length / 2), encoded.length - 1, encoded.length]) {
    await page.locator("[data-woge-region]").evaluate((element) => {
      element.innerHTML = "<p>Original</p>";
      element.dataset.wogeRevision = "0";
    });
    const result = await apply(page, encoded, split);
    expect(result.error, `split ${split}`).toBeNull();
    expect(result.html).toContain("Split safely");
  }
});

test("rejects scripts and inline handlers without changing the DOM", async ({ page }) => {
  for (const html of [
    "<p>Unsafe</p><script>globalThis.__patchScriptRan = true</script>",
    '<img src="missing" onerror="globalThis.__patchScriptRan = true">',
    '<a href="javascript:globalThis.__patchScriptRan=true">Unsafe</a>',
    '<template><script>globalThis.__patchScriptRan = true</script></template>'
  ]) {
    const encoded = encodeFrames([patchFrame({ html }), completeFrame()]);
    const result = await apply(page, encoded, "one-byte");
    expect(result.error).toMatch(/^WOGE_PATCH_ACTIVE_/);
    expect(result.html).toBe("<p>Original</p>");
    expect(await page.evaluate(() => globalThis.__patchScriptRan)).toBeUndefined();
  }
});

test("fails safely for unknown targets and stale revisions", async ({ page }) => {
  const unknown = await apply(page, encodeFrames([patchFrame({ target: "missing" }), completeFrame()]), 11);
  expect(unknown.error).toBe("WOGE_TARGET_UNKNOWN");
  expect(unknown.html).toBe("<p>Original</p>");

  const stale = await apply(page, encodeFrames([patchFrame({ baseRevision: 9, nextRevision: 10 }), completeFrame()]), 13);
  expect(stale.error).toBe("WOGE_REVISION_MISMATCH");
  expect(stale.html).toBe("<p>Original</p>");
});

test("fails safely for duplicate targets and malformed or error frames", async ({ page }) => {
  await page.locator("body").evaluate((body) => {
    body.insertAdjacentHTML("beforeend", '<aside data-woge-region="summary-1" data-woge-revision="0"></aside>');
  });
  const duplicate = await apply(page, encodeFrames([patchFrame(), completeFrame()]), 10);
  expect(duplicate.error).toBe("WOGE_TARGET_DUPLICATE");

  await page.locator("aside").evaluate((element) => element.remove());
  const complete = encodeFrames([patchFrame(), completeFrame()]);
  const truncated = await apply(page, complete.slice(0, -1), "one-byte");
  expect(truncated.error).toBe("WOGE_TRUNCATED_FRAME");

  const remoteError = await apply(page, encodeFrames([errorFrame()]), 7);
  expect(remoteError.error).toBe("WOGE_RENDER_FAILED");
});

test("rejects malformed protocol bytes before changing the DOM", async ({ page }) => {
  const valid = encodeFrames([patchFrame(), completeFrame()]);
  const malformed = [];

  const badMagic = valid.slice();
  badMagic[0] = 0;
  malformed.push([badMagic, "WOGE_PROTOCOL_VERSION"]);

  const oversizedMetadata = valid.slice();
  new DataView(oversizedMetadata.buffer).setUint32(7, 64 * 1024 + 1, false);
  malformed.push([oversizedMetadata, "WOGE_METADATA_LIMIT"]);

  const nonAsciiContentType = valid.slice();
  nonAsciiContentType[15] = 0xff;
  malformed.push([nonAsciiContentType, "WOGE_CONTENT_TYPE_ENCODING"]);

  const invalidMetadata = valid.slice();
  const metadataStart = 15 + invalidMetadata[6];
  invalidMetadata[metadataStart] = "!".charCodeAt(0);
  malformed.push([invalidMetadata, "WOGE_METADATA_JSON"]);

  const terminalOnly = encodeFrames([completeFrame()]);
  const trailingByte = new Uint8Array(terminalOnly.byteLength + 1);
  trailingByte.set(terminalOnly);
  trailingByte[terminalOnly.byteLength] = 1;
  malformed.push([trailingByte, "WOGE_BYTES_AFTER_TERMINAL"]);

  for (const [bytes, expectedError] of malformed) {
    const result = await apply(page, bytes, "one-byte");
    expect(result.error).toBe(expectedError);
    expect(result.html).toBe("<p>Original</p>");
    expect(result.revision).toBe("0");
  }
});

async function apply(page, encoded, split) {
  return page.evaluate(
    async ({ bytes, splitAt }) => {
      const all = Uint8Array.from(bytes);
      const chunks = splitAt === "one-byte"
        ? Array.from(all, (byte) => Uint8Array.of(byte))
        : [all.slice(0, splitAt), all.slice(splitAt)];
      const stream = new ReadableStream({
        start(controller) {
          chunks.forEach((chunk) => controller.enqueue(chunk));
          controller.close();
        }
      });
      let error = null;
      try {
        await globalThis.WogeFallback.applyPatchStream(stream);
      } catch (failure) {
        error = failure.code || failure.name;
      }
      const region = document.querySelector('[data-woge-region="summary-1"]');
      return { error, html: region.innerHTML, revision: region.dataset.wogeRevision };
    },
    { bytes: Array.from(encoded), splitAt: split }
  );
}

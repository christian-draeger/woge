import { createHash } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { brotliCompressSync, gzipSync } from "node:zlib";

const sourceFiles = ["index.js", "protocol.js", "dom.js"];
const sources = await Promise.all(
  sourceFiles.map((name) => readFile(new URL(`../src/${name}`, import.meta.url))),
);
const bundle = await readFile(new URL("../dist/woge-fallback.js", import.meta.url));
const metrics = {
  sourceBytes: sources.reduce((size, source) => size + source.byteLength, 0),
  minifiedBundleBytes: bundle.byteLength,
  gzipBytes: gzipSync(bundle, { level: 9 }).byteLength,
  brotliBytes: brotliCompressSync(bundle).byteLength,
  sha256: createHash("sha256").update(bundle).digest("hex"),
};

await mkdir(new URL("../build/", import.meta.url), { recursive: true });
await writeFile(new URL("../build/size-metrics.json", import.meta.url), `${JSON.stringify(metrics, null, 2)}\n`);

console.log(
  `[woge-metrics] minified_bytes=${metrics.minifiedBundleBytes} gzip_bytes=${metrics.gzipBytes} ` +
    `brotli_bytes=${metrics.brotliBytes} sha256=${metrics.sha256}`,
);

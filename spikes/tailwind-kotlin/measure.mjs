import { createHash } from "node:crypto";
import { gzipSync } from "node:zlib";
import { readFile } from "node:fs/promises";
import { performance } from "node:perf_hooks";
import { buildTailwind } from "./build-tailwind.mjs";

const durations = [];
const hashes = [];
let cssBytes = 0;
let gzipBytes = 0;
let sourceMapBytes = 0;

for (let index = 0; index < 5; index += 1) {
  const output = "build/measure/tailwind.min.css";
  const started = performance.now();
  await buildTailwind({ input: "src/main/css/tailwind.css", output, minify: true });
  durations.push(performance.now() - started);

  const contents = await readFile(output, "utf8");
  const sourceMap = await readFile(`${output}.map`);
  cssBytes = Buffer.byteLength(contents);
  gzipBytes = gzipSync(contents).byteLength;
  sourceMapBytes = sourceMap.byteLength;
  hashes.push(createHash("sha256").update(contents).digest("hex"));
}

if (new Set(hashes).size !== 1) throw new Error("production output changed across identical builds");
durations.sort((left, right) => left - right);

console.log(JSON.stringify({
  tailwindVersion: "4.3.3",
  runs: durations.length,
  buildMilliseconds: {
    minimum: Number(durations[0].toFixed(1)),
    median: Number(durations[Math.floor(durations.length / 2)].toFixed(1)),
    maximum: Number(durations.at(-1).toFixed(1))
  },
  output: {
    cssBytes,
    gzipBytes,
    sourceMapBytes,
    sha256: hashes[0]
  }
}, null, 2));

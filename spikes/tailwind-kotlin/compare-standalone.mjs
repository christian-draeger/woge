import { readFile } from "node:fs/promises";
import assert from "node:assert/strict";
import { buildTailwind } from "./build-tailwind.mjs";

const standalone = process.env.TAILWIND_STANDALONE;
if (!standalone) throw new Error("TAILWIND_STANDALONE must point to the pinned Tailwind executable");

await buildTailwind({
  input: "src/main/css/tailwind.css",
  output: "build/standalone/node.css",
  minify: true
});
await buildTailwind({
  input: "src/main/css/tailwind.css",
  output: "build/standalone/standalone.css",
  minify: true,
  executable: standalone
});

const sourceMapPattern = /\/\*# sourceMappingURL=[^ ]+ \*\//;
const nodeCss = (await readFile("build/standalone/node.css", "utf8")).replace(sourceMapPattern, "");
const standaloneCss = (await readFile("build/standalone/standalone.css", "utf8")).replace(sourceMapPattern, "");
assert.equal(standaloneCss, nodeCss, "standalone and npm CLIs emitted different CSS");

console.log(JSON.stringify({
  version: "4.3.3",
  cssBytes: Buffer.byteLength(nodeCss),
  identicalCss: true
}, null, 2));

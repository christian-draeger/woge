import { createHash } from "node:crypto";
import { readFile } from "node:fs/promises";
import test from "node:test";
import assert from "node:assert/strict";
import { buildTailwind } from "../build-tailwind.mjs";

const productionPath = "build/generated-resources/main/static/assets/tailwind.min.css";
const sourceMapPattern = /\/\*# sourceMappingURL=tailwind\.min\.css\.map \*\//;

test("extracts Kotlin, generated, distributed and explicit candidates", async () => {
  const { css } = splitOutput(await readFile(productionPath, "utf8"));

  for (const selector of [
    ".grid{",
    ".bg-brand-500{",
    ".hover\\:shadow-lg:hover{",
    ".md\\:grid-cols-2{",
    ".\\[grid-template-columns\\:minmax\\(0\\,1fr\\)_auto\\]{",
    ".ring-brand-500{",
    ".rounded-xl{",
    ".supports-\\[display\\:grid\\]\\:grid{",
    ".motion-safe\\:animate-spin{"
  ]) {
    assert.ok(css.includes(selector), `missing generated selector ${selector}`);
  }
  assert.ok(!css.includes(".bg-red-500{"), "a dynamically constructed class must not appear accidentally");
});

test("copies application-owned modern CSS without passing it through Tailwind", async () => {
  const { css } = splitOutput(await readFile(productionPath, "utf8"));
  const source = await readFile("src/main/css/application.css");
  const copied = await readFile("build/generated-resources/main/static/assets/application.css");

  assert.deepEqual(copied, source);
  assert.match(source.toString(), /@layer application-components/);
  assert.match(source.toString(), /container-type: inline-size/);
  assert.match(source.toString(), /@container \(width >= 36rem\)/);
  assert.ok(!css.includes(".project-card{"));
});

test("normalizes and externalizes the source map for reproducible checkouts", async () => {
  const output = await readFile(productionPath, "utf8");
  const mapSource = await readFile(`${productionPath}.map`, "utf8");
  const map = JSON.parse(mapSource);

  assert.deepEqual(map.sources, ["src/main/css/tailwind.css", "tailwindcss/index.css"]);
  assert.equal(map.sourcesContent.length, 2);
  assert.match(map.sourcesContent[0], /@theme/);
  assert.ok(map.mappings.length > 0);
  assert.ok(!output.includes("A200296237"));
  assert.ok(!mapSource.includes("A200296237"));
  assert.match(output, sourceMapPattern);
});

test("production output is byte-for-byte reproducible", async () => {
  const repeatedPath = "build/test-output/tailwind.min.css";
  await buildTailwind({
    input: "src/main/css/tailwind.css",
    output: repeatedPath,
    minify: true
  });
  const first = await readFile(productionPath);
  const repeated = await readFile(repeatedPath);
  const firstMap = await readFile(`${productionPath}.map`);
  const repeatedMap = await readFile(`${repeatedPath}.map`);

  assert.equal(createHash("sha256").update(first).digest("hex"), createHash("sha256").update(repeated).digest("hex"));
  assert.deepEqual(repeated, first);
  assert.deepEqual(repeatedMap, firstMap);
});

function splitOutput(output) {
  assert.match(output, sourceMapPattern);
  return {
    css: output.replace(sourceMapPattern, "")
  };
}

import { readFile, writeFile, mkdir, readdir } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { build } from "esbuild";
import { spawnSync } from "node:child_process";
import { sizes, verifyBudgets, verifyGraph } from "./budget-policy.mjs";

const root = new URL("../../../", import.meta.url);
const directory = new URL("../build/budgets/", import.meta.url);
await mkdir(directory, { recursive: true });
const actual = {};
async function measure(name, path) { actual[name] = sizes(await readFile(path)); }
async function bundle(name, path) {
  const result = await build({
    entryPoints: [fileURLToPath(path)], bundle: true, write: false, minify: true,
    external: ["./transactions.js"], format: "esm", target: "es2022", legalComments: "none", metafile: true,
  });
  verifyGraph(result, name.startsWith("core-") ? ["./transactions.js"] : []);
  actual[name] = sizes(result.outputFiles[0].contents);
}
await measure("navigation-module.js", new URL("../dist/woge-navigation.js", import.meta.url));
for (const name of ["state.js"]) {
  await measure(`navigation-shared/${name}`, new URL(`../dist/${name}`, import.meta.url));
}
await bundle("navigation-graph.js", new URL("../src/navigation.js", import.meta.url));
await measure("islands-module.js", new URL("../dist/islands.js", import.meta.url));
await bundle("islands-graph.js", new URL("../src/islands.js", import.meta.url));
await measure("transaction-module.js", new URL("../dist/transactions.js", import.meta.url));
await bundle("transaction-graph.js", new URL("../src/transactions.js", import.meta.url));
await measure("core-bundle.js", new URL("../dist/woge-fallback.js", import.meta.url));
// Also gate the unbundled classpath delivery used by plain Spring/Ktor applications.
for (const name of (await readdir(new URL("../src/", import.meta.url))).filter((name) => name.endsWith(".js")).sort()) {
  await measure(`classpath/${name}`, new URL(`../src/${name}`, import.meta.url));
}
await bundle("core-graph.js", new URL("../src/index.js", import.meta.url));
async function assets(path, prefix) {
  for (const entry of await readdir(path, { withFileTypes: true })) {
    const child = new URL(entry.name + (entry.isDirectory() ? "/" : ""), path);
    if (entry.isDirectory()) await assets(child, `${prefix}${entry.name}/`);
    else if (/\.(js|css)$/.test(entry.name)) {
      // Raw shipped bytes, not a hypothetical minified component.
      await measure(`${prefix}${entry.name}`, child);
      if (entry.name.endsWith(".js")) await bundle(`${prefix}${entry.name}:graph`, child);
    }
  }
}
for (const module of (await readdir(new URL("modules/", root))).filter((name) => name.startsWith("woge-ui-"))) {
  await assets(new URL(`modules/${module}/src/main/resources/`, root), `${module}/`);
}
await bundle("reference-application.css", new URL("examples/reference-application/shared/src/main/resources/static/assets/application.css", root));
await bundle("plain-card.css", new URL("testing/performance/assets/plain.css", root));
await bundle("optional-theme.css", new URL("testing/performance/assets/theme.css", root));
const input = new URL("tailwind.css", directory);
const output = new URL("tailwind-output.css", directory);
await writeFile(input, '@import "tailwindcss" source(none);\n@source "../../../../testing/performance/assets/tailwind.html";\n');
const compiler = spawnSync(process.execPath, [
  fileURLToPath(new URL("../node_modules/@tailwindcss/cli/dist/index.mjs", import.meta.url)),
  "-i", fileURLToPath(input), "-o", fileURLToPath(output), "--minify",
], { encoding: "utf8" });
if (compiler.status !== 0) throw new Error(`Tailwind measurement failed: ${compiler.stderr}`);
const tailwindCss = await readFile(output, "utf8");
for (const selector of [".flex{", ".sm\\:flex-row{", ".focus-visible\\:outline-offset-2:focus-visible{"]) {
  if (!tailwindCss.includes(selector)) throw new Error(`Equivalent Tailwind fixture lost ${selector}`);
}
await measure("tailwind-card.css", output);
const baselinePath = new URL("testing/performance/asset-baseline.json", root);
if (process.env.WOGE_BUDGETS_UPDATE === "true") {
  // Ceiling policy is never raised by the update switch. A new inventory needs explicit review.
  const baseline = JSON.parse(await readFile(baselinePath, "utf8"));
  if (JSON.stringify(Object.keys(actual).sort()) !== JSON.stringify(Object.keys(baseline.assets).sort())) {
    throw new Error("Register new asset ceilings explicitly before updating measurements");
  }
  for (const [name, value] of Object.entries(actual)) {
    baseline.assets[name].measured = value;
    for (const encoding of Object.keys(value)) {
      if (value[encoding] > baseline.assets[name].ceiling[encoding]) throw new Error(`${name} exceeds fixed ceiling`);
    }
  }
  verifyBudgets(actual, baseline);
  await writeFile(baselinePath, `${JSON.stringify(baseline, null, 2)}\n`);
}
verifyBudgets(actual, JSON.parse(await readFile(baselinePath, "utf8")));
await writeFile(new URL("../build/budget-metrics.json", import.meta.url), `${JSON.stringify(actual, null, 2)}\n`);
console.log(`[woge-budgets] ${Object.keys(actual).length} assets passed`);

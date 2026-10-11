import assert from "node:assert/strict";
import { createServer } from "node:http";
import { readFile, writeFile, mkdir, cp } from "node:fs/promises";
import { dirname, resolve, extname } from "node:path";
import { fileURLToPath } from "node:url";
import { gzipSync, brotliCompressSync, constants } from "node:zlib";
import { createRequire } from "node:module";
import os from "node:os";
import { checkDebugging } from "./debug.mjs";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const require = createRequire(resolve(root, "../../client/woge-fallback-client/package.json"));
const { chromium } = require("@playwright/test");
const esbuild = require("esbuild");
const runs = Number(process.env.RUNS ?? 30);
assert.ok(Number.isInteger(runs) && runs >= 3);
const dist = resolve(root, "build/site");
await mkdir(dist, { recursive: true });
for (const variant of ["js-direct", "js-browser", "wasm"]) {
  const wasm = variant === "wasm";
  const source = resolve(root, variant, `build/compileSync/${wasm ? "wasmJs" : "js"}/main/productionExecutable/${wasm ? "optimized" : "kotlin"}`);
  await mkdir(resolve(dist, variant), { recursive: true });
  await esbuild.build({
    entryPoints: [resolve(source, `local-island-target-${variant}.mjs`)],
    outfile: resolve(dist, variant, "island.mjs"),
    bundle: true, minify: true, format: "esm", target: "es2022", platform: "browser",
    sourcemap: "linked", external: ["node:*", "https://*"],
  });
  if (wasm) {
    const binary = "local-island-target-wasm.wasm";
    await cp(resolve(source, binary), resolve(dist, variant, binary));
    const map = JSON.parse(await readFile(resolve(source, `${binary}.map`), "utf8"));
    map.sourcesContent = await Promise.all(map.sources.map(async (s) => {
      try { return await readFile(resolve(source, s), "utf8"); } catch { return null; }
    }));
    await writeFile(resolve(dist, variant, `${binary}.map`), JSON.stringify(map));
  }
}
await mkdir(resolve(dist, "dual"), { recursive: true });
await esbuild.build({ entryPoints: [resolve(root, "web/dual.mjs")], outfile: resolve(dist, "dual/loader.mjs"), minify: true, format: "esm", target: "es2022" });
const template = await readFile(resolve(root, "web/index.html"), "utf8");
const notes = [];
const escape = s => s.replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const server = createServer(async (request, response) => {
  try {
    const url = new URL(request.url, "http://localhost");
    response.setHeader("Cache-Control", "no-store");
    if (request.method === "POST" && url.pathname === "/notes") {
      let body = "";
      for await (const chunk of request) body += chunk;
      const text = new URLSearchParams(body).get("text") ?? "";
      if (!text.trim() || text.length > 120) { response.writeHead(400); response.end("Invalid note"); return; }
      notes.push(text.trim());
      response.writeHead(303, { Location: "/" }); response.end(); return;
    }
    if (url.pathname === "/") {
      const variant = url.searchParams.get("variant");
      const entry = variant?.startsWith("dual") ? "/dual/loader.mjs" : `/${variant}/island.mjs`;
      response.setHeader("Content-Type", "text/html; charset=utf-8");
      response.end(template.replace("</ul>", notes.map(n => `<li>${escape(n)}</li>`).join("") + "</ul>")
        .replace("</html>", variant ? `<script type="module">globalThis.__islandStart = performance.now(); await import('${entry}');</script></html>` : "</html>"));
      return;
    }
    if (url.pathname.startsWith("/source/")) {
      const path = resolve(root, url.pathname.slice(8));
      assert.ok(path.startsWith(root + "/") && path.endsWith(".kt"));
      response.setHeader("Content-Type", "text/plain"); response.end(await readFile(path)); return;
    }
    const path = resolve(dist, "." + url.pathname);
    assert.ok(path.startsWith(dist + "/"));
    const bytes = await readFile(path);
    response.setHeader("Content-Type", extname(path) === ".wasm" ? "application/wasm" : "text/javascript");
    response.end(bytes);
  } catch { response.writeHead(404); response.end("Not found"); }
});
await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
const origin = `http://127.0.0.1:${server.address().port}`;
const browser = await chromium.launch();
const results = [];
try {
  const baseline = await browser.newContext({ javaScriptEnabled: false });
  const page = await baseline.newPage();
  await page.goto(origin);
  await page.getByLabel("New note").fill("HTML still works");
  await Promise.all([page.waitForNavigation(), page.getByRole("button", { name: "Add note" }).click()]);
  assert.match(await page.locator("ul").textContent(), /HTML still works/);
  await baseline.close();
  for (const variant of ["js-direct", "js-browser", "wasm", "dual-wasm", "dual-js", "dual-error"]) {
    const times = []; let files; let target; let documentBytes;
    for (let i = 0; i < runs; i++) {
      const context = await browser.newContext();
      if (variant === "dual-js") await context.addInitScript(() => { globalThis.WebAssembly = undefined; });
      const page = await context.newPage();
      if (variant === "dual-error") await context.addInitScript(() => {
        WebAssembly.instantiateStreaming = async response => {
          await (await response).arrayBuffer();
          throw new WebAssembly.CompileError("Injected pre-mount instantiation failure");
        };
      });
      const errors = []; page.on("pageerror", error => errors.push(error.message));
      const downloaded = new Set();
      page.on("response", response => {
        const path = new URL(response.url()).pathname;
        if (/\.(mjs|wasm)$/.test(path)) downloaded.add(path);
      });
      const navigation = await page.goto(`${origin}/?variant=${variant}`);
      documentBytes = Buffer.from(await navigation.text());
      await page.waitForFunction(() => globalThis.__islandReady !== undefined);
      times.push(await page.evaluate(() => globalThis.__islandReady - globalThis.__islandStart));
      assert.deepEqual(errors, []);
      assert.match(await page.locator("#note-counter").textContent(), /^0\/120/);
      assert.equal(await page.locator("#note-text").getAttribute("aria-invalid"), "true");
      await page.getByLabel("New note").fill("Water the herbs");
      assert.match(await page.locator("#note-counter").textContent(), /^15\/120 characters$/);
      assert.equal(await page.locator("#note-text").getAttribute("aria-invalid"), "false");
      await page.evaluate(() => {
        const input = document.getElementById("note-text"); input.value = "x".repeat(121); input.dispatchEvent(new Event("input"));
      });
      assert.equal(await page.locator("#note-text").getAttribute("aria-invalid"), "true");
      if (variant.startsWith("dual")) {
        await page.waitForFunction(() => globalThis.__islandTarget !== undefined);
        target = await page.evaluate(() => globalThis.__islandTarget);
        assert.equal(target, variant === "dual-wasm" ? "wasm" : "js");
      }
      files = [...downloaded].sort();
      await context.close();
    }
    const sizes = [];
    for (const file of files) {
      const bytes = await readFile(resolve(dist, "." + file));
      sizes.push({ file, raw: bytes.length, gzip: gzipSync(bytes, { level: 9 }).length,
        brotli: brotliCompressSync(bytes, { params: { [constants.BROTLI_PARAM_QUALITY]: 11 } }).length });
    }
    const document = { raw: documentBytes.length, gzip: gzipSync(documentBytes, { level: 9 }).length,
      brotli: brotliCompressSync(documentBytes, { params: { [constants.BROTLI_PARAM_QUALITY]: 11 } }).length };
    const total = Object.fromEntries(["raw", "gzip", "brotli"].map(key => [key, document[key] + sizes.reduce((n, s) => n + s[key], 0)]));
    times.sort((a, b) => a - b);
    results.push({ variant, target, document, total, files: sizes, raw: sizes.reduce((n, s) => n + s.raw, 0), gzip: sizes.reduce((n, s) => n + s.gzip, 0),
      brotli: sizes.reduce((n, s) => n + s.brotli, 0), medianMs: (times[Math.floor((runs - 1) / 2)] + times[Math.floor(runs / 2)]) / 2,
      minMs: times[0], maxMs: times.at(-1), samplesMs: times });
  }
  const mapChecks = {};
  for (const variant of ["js-direct", "js-browser", "wasm"]) {
    const map = JSON.parse(await readFile(resolve(dist, variant, variant === "wasm" ? "local-island-target-wasm.wasm.map" : "island.mjs.map"), "utf8"));
    const source = map.sources.findIndex(s => s.endsWith("Island.kt"));
    assert.ok(source >= 0 && map.mappings.length > 0 && map.sourcesContent[source]?.includes("fun main()"));
    mapChecks[variant] = { sources: map.sources.length, kotlinSourceEmbedded: true };
  }
  const debugging = await checkDebugging(browser, origin, dist);
  const output = { debugging, measuredAt: new Date().toISOString(), node: process.version, chromium: browser.version(), playwright: require("@playwright/test/package.json").version,
    esbuild: esbuild.version, machine: `${os.type()} ${os.release()} ${os.arch()} ${os.cpus()[0].model}`, runs,
    compression: "per downloaded file: gzip level 9, brotli quality 11; totals include HTML; maps are debugger-only", mapChecks, results };
  await writeFile(resolve(root, "build/results.json"), JSON.stringify(output, null, 2) + "\n");
  console.log(JSON.stringify(output, null, 2));
} finally {
  await browser.close(); await new Promise(resolve => server.close(resolve));
}

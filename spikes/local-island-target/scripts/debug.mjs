import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { resolve } from "node:path";

function mappings(map) {
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
  let source = 0, originalLine = 0, originalColumn = 0;
  const locations = [];
  for (const [line, segments] of map.mappings.split(";").entries()) {
    let column = 0;
    for (const segment of segments.split(",")) {
      const values = []; let value = 0, shift = 0;
      for (const char of segment) {
        const digit = alphabet.indexOf(char);
        value |= (digit & 31) << shift;
        if (digit & 32) shift += 5;
        else { values.push((value & 1) ? -(value >> 1) : value >> 1); value = 0; shift = 0; }
      }
      column += values[0] ?? 0;
      if (values.length >= 4) {
        source += values[1]; originalLine += values[2]; originalColumn += values[3];
        locations.push({ lineNumber: line, columnNumber: column, source, originalLine });
      }
    }
  }
  return locations;
}

export async function checkDebugging(browser, origin, dist) {
  const results = {};
  for (const variant of ["js-direct", "js-browser", "wasm"]) {
    const wasm = variant === "wasm";
    const file = wasm ? "local-island-target-wasm.wasm.map" : "island.mjs.map";
    const map = JSON.parse(await readFile(resolve(dist, variant, file), "utf8"));
    const source = map.sources.findIndex(s => s.endsWith(wasm ? "Island.kt" : "Dom.kt"));
    const originalLine = map.sourcesContent[source].split("\n").findIndex(s => s.includes(wasm ? 'showStatus("' : '.textContent = message'));
    const mapped = mappings(map).find(p => p.source === source && p.originalLine === originalLine);
    assert.ok(mapped, `${variant}: status update has a generated source-map location`);
    const context = await browser.newContext();
    const page = await context.newPage();
    const cdp = await context.newCDPSession(page);
    const scripts = [];
    cdp.on("Debugger.scriptParsed", script => scripts.push(script));
    await cdp.send("Debugger.enable");
    await page.goto(`${origin}/?variant=${variant}`);
    await page.waitForFunction(() => globalThis.__islandReady !== undefined);
    const script = scripts.find(s => wasm ? s.scriptLanguage === "WebAssembly" && s.length > 10000 : s.url.endsWith(`/${variant}/island.mjs`));
    assert.ok(script, `${variant}: Chromium debugger sees compiled script`);
    const breakpoint = await cdp.send("Debugger.setBreakpoint", { location: {
      scriptId: script.scriptId, lineNumber: mapped.lineNumber, columnNumber: mapped.columnNumber,
    } }).catch(error => ({ error: error.message }));
    let timer;
    const paused = new Promise(resolve => {
      cdp.once("Debugger.paused", async event => {
        await cdp.send("Debugger.resume"); resolve(event.callFrames[0]?.location);
      });
      timer = setTimeout(() => resolve(null), 3000);
    });
    const interaction = page.evaluate(() => {
      const input = document.getElementById("note-text"); input.value = "Debug event"; input.dispatchEvent(new Event("input"));
    });
    const hit = await paused; clearTimeout(timer); await interaction;
    if (!wasm) assert.ok(hit, `${variant}: mapped Kotlin breakpoint pauses on input`);
    results[variant] = { kotlinLine: originalLine + 1, source: map.sources[source], sourceMapURL: script.sourceMapURL ?? null,
      breakpointHit: !!hit, breakpoint: breakpoint.actualLocation ?? breakpoint.error };
    await context.close();
  }
  return results;
}

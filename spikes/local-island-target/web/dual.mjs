// Probe GC struct types and exception tags; instantiation remains the final capability check.
export function supportsWasm() {
  try {
    return typeof WebAssembly === "object" && typeof WebAssembly.Exception === "function" &&
      WebAssembly.validate(Uint8Array.from([0,97,115,109,1,0,0,0,1,5,1,95,1,127,0])) &&
      WebAssembly.validate(Uint8Array.from([0,97,115,109,1,0,0,0,1,4,1,96,0,0,13,3,1,0,0]));
  } catch { return false; }
}

if (supportsWasm()) {
  try {
    await import("../wasm/island.mjs");
    globalThis.__islandTarget = "wasm";
  } catch (error) {
    // Compiler/engine incompatibility before main starts can still use the JS artifact.
    if (globalThis.__islandReady !== undefined) throw error;
    await import("../js-browser/island.mjs");
    globalThis.__islandTarget = "js";
  }
} else {
  await import("../js-browser/island.mjs");
  globalThis.__islandTarget = "js";
}

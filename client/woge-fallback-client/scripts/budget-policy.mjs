import { brotliCompressSync, gzipSync } from "node:zlib";

export function sizes(bytes) {
  return {
    raw: bytes.byteLength,
    gzip: gzipSync(bytes, { level: 9 }).byteLength,
    brotli: brotliCompressSync(bytes).byteLength,
  };
}

export function verifyBudgets(actual, baseline) {
  const keys = Object.keys(actual).sort();
  if (JSON.stringify(keys) !== JSON.stringify(Object.keys(baseline.assets).sort())) {
    throw new Error("Asset inventory changed: explicitly review and register every new asset budget");
  }
  for (const key of keys) {
    for (const encoding of ["raw", "gzip", "brotli"]) {
      const value = actual[key][encoding];
      const { measured, ceiling } = baseline.assets[key];
      const allowed = Math.min(ceiling[encoding], Math.ceil(measured[encoding] * (1 + baseline.tolerance)));
      if (!Number.isFinite(value) || !Number.isFinite(allowed) || value > allowed) {
        throw new Error(`${key} ${encoding}: ${value} bytes exceeds ${allowed}; review, do not auto-rebaseline`);
      }
    }
  }
  for (const encoding of ["raw", "gzip", "brotli"]) {
    const limit = actual["plain-card.css"][encoding] * baseline.tailwindMaxRatio + baseline.tailwindAllowanceBytes[encoding];
    if (actual["tailwind-card.css"][encoding] > limit) {
      throw new Error(`Tailwind ${encoding} exceeds equivalent plain-CSS ratio plus allowance (${limit})`);
    }
  }
}

export function verifyGraph(result) {
  for (const input of Object.keys(result.metafile.inputs)) {
    if (input.includes("node_modules/")) throw new Error(`Application-wide dependency in runtime asset: ${input}`);
  }
  for (const output of Object.values(result.metafile.outputs)) {
    if (output.imports.length) throw new Error("Runtime assets must be self-contained; external imports are not budgeted");
  }
}

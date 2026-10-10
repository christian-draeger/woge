import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync } from "node:fs";
import { join } from "node:path";
import { test } from "node:test";

const spike = join(import.meta.dirname, "..");

function build(environment = {}) {
  mkdirSync(join(spike, "build"), { recursive: true });
  const outDir = mkdtempSync(join(spike, "build", "test-"));
  const result = spawnSync(
    process.execPath,
    [join(spike, "node_modules/vite/bin/vite.js"), "build", "--config", "vite.config.mjs", "--logLevel", "error"],
    { cwd: spike, env: { ...process.env, VITE_OUT_DIR: outDir, ...environment }, encoding: "utf8" },
  );
  assert.equal(result.status, 0, result.stderr);
  return outDir;
}

const tree = (directory) =>
  readdirSync(directory, { recursive: true, withFileTypes: true })
    .filter((entry) => entry.isFile())
    .map((entry) => join(entry.parentPath, entry.name).slice(directory.length + 1))
    .sort();

test("stable names leave content hashing to the Woge asset tree", () => {
  const outDir = build();
  try {
    assert.deepEqual(tree(outDir), ["chunks/chart.js", "main.css", "main.js"]);
    const main = readFileSync(join(outDir, "main.js"), "utf8");
    assert.match(main, /import\(`\.\/chunks\/chart\.js`\)/, "chunk import stays relative to the entry");
    assert.doesNotMatch(main, /\beval\(|new Function\(/, "output works under a strict script-src CSP");
  } finally {
    rmSync(outDir, { recursive: true });
  }
});

test("Vite processes imported CSS instead of preserving it byte for byte", () => {
  const outDir = build();
  try {
    const css = readFileSync(join(outDir, "main.css"), "utf8");
    assert.match(css, /\.card h2\{/, "native nesting is lowered");
    assert.match(css, /\.rounded-lg\{/, "Tailwind output is merged into the same file");
  } finally {
    rmSync(outDir, { recursive: true });
  }
});

test("opt-in source maps keep project-relative sources", () => {
  const outDir = build({ VITE_HASHED: "true", VITE_SOURCEMAP: "true" });
  try {
    const maps = tree(outDir).filter((file) => file.endsWith(".map"));
    assert.ok(maps.length > 0);
    for (const map of maps) {
      for (const source of JSON.parse(readFileSync(join(outDir, map), "utf8")).sources) {
        assert.ok(!source.includes(spike) && !source.startsWith("/"), source);
      }
    }
  } finally {
    rmSync(outDir, { recursive: true });
  }
});

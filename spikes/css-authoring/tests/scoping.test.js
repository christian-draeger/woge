import { readFile } from "node:fs/promises";
import test from "node:test";
import assert from "node:assert/strict";
import { scopeAttribute, scopeCss, scopeId } from "../scoping.mjs";

const componentId = "dev.woge.reference.ProjectCard";
const fixtureUrl = new URL("../fixtures/component.css", import.meta.url);
const css = await readFile(fixtureUrl, "utf8");

test("scope identity is deterministic and independent of CSS edits", () => {
  assert.equal(scopeId(componentId), scopeId(componentId));
  assert.notEqual(scopeId(componentId), scopeId("dev.woge.reference.ProjectList"));
  assert.deepEqual(scopeAttribute(componentId), { name: "data-woge-scope", value: scopeId(componentId) });
});

test("rewrites local selector subjects while preserving explicit globals", async () => {
  const result = await scopeCss({ componentId, css, from: "ProjectCard.kt.css" });
  const marker = `:where([data-woge-scope=${result.scope.value}])`;

  assert.match(result.css, new RegExp(`\\.card${escapeRegex(marker)}`));
  assert.match(result.css, new RegExp(`article:hover${escapeRegex(marker)}::before`));
  assert.match(result.css, new RegExp(`& > \\.title${escapeRegex(marker)}`));
  assert.match(result.css, new RegExp(`\\.theme-dark \\.card${escapeRegex(marker)}`));
  assert.match(result.css, new RegExp(`\\.card${escapeRegex(marker)} \\.third-party`));
  assert.match(result.css, /html\s*\{/);
  assert.doesNotMatch(result.css, new RegExp(`html${escapeRegex(marker)}`));
});

test("scopes local keyframes and animation references but preserves global keyframes", async () => {
  const result = await scopeCss({ componentId, css, from: "ProjectCard.kt.css" });
  const scopedPulse = `pulse-${result.scope.value}`;

  assert.equal(result.keyframes.pulse, scopedPulse);
  assert.match(result.css, new RegExp(`@keyframes ${scopedPulse}`));
  assert.match(result.css, new RegExp(`animation: ${scopedPulse} 1s ease, spin 2s linear`));
  assert.match(result.css, /@keyframes spin/);
});

test("preserves modern at-rules and emits an external source map", async () => {
  const result = await scopeCss({ componentId, css, from: "ProjectCard.kt.css" });

  assert.match(result.css, /@layer woge-components/);
  assert.match(result.css, /@container \(width >= 30rem\)/);
  assert.match(result.css, /@supports \(color: oklch/);
  assert.match(result.css, /@media \(prefers-reduced-motion: reduce\)/);
  assert.deepEqual(result.map.sources, ["ProjectCard.kt.css"]);
  assert.deepEqual(result.map.sourcesContent, [css]);
  assert.ok(result.map.mappings.length > 0);
});

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

import test from "node:test";
import assert from "node:assert/strict";
import { findDynamicUtilities } from "../candidate-policy.mjs";

test("accepts complete static Kotlin candidates", async () => {
  assert.deepEqual(
    await findDynamicUtilities(["src/main/kotlin", "fixtures/source-distributed"]),
    []
  );
});

test("reports dynamically assembled utility names with source location", async () => {
  const findings = await findDynamicUtilities(["fixtures/negative/DynamicClass.kt"]);

  assert.equal(findings.length, 1);
  assert.match(findings[0].path, /DynamicClass\.kt$/);
  assert.equal(findings[0].line, 3);
  assert.match(findings[0].text, /bg-/);
});

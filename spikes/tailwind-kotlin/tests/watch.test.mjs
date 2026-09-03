import { spawn } from "node:child_process";
import { readFile, rm, writeFile } from "node:fs/promises";
import test from "node:test";
import assert from "node:assert/strict";

const generatedSource = "build/generated/sources/woge/dev/woge/generated/ProjectRegionStyles.kt";
const output = "build/watch-test/application.css";

test("polling watch rebuilds after a generated Kotlin source changes", { timeout: 15_000 }, async () => {
  const original = await readFile(generatedSource, "utf8");
  await rm(output, { force: true });
  const child = spawn(
    process.execPath,
    [
      "node_modules/@tailwindcss/cli/dist/index.mjs",
      "--input", "src/main/css/tailwind.css",
      "--output", output,
      "--watch=always",
      "--poll=100",
      "--silent"
    ],
    { cwd: process.cwd(), stdio: ["ignore", "ignore", "pipe"] }
  );
  let errors = "";
  child.stderr.on("data", (chunk) => { errors += chunk; });

  try {
    await waitForCss(".animate-pulse");
    await writeFile(generatedSource, `${original}\n// underline\n`);
    await waitForCss(".underline");
    assert.equal(child.exitCode, null, errors);
  } finally {
    await writeFile(generatedSource, original);
    child.kill("SIGTERM");
    await new Promise((resolve) => child.once("exit", resolve));
  }
});

async function waitForCss(fragment) {
  const deadline = Date.now() + 8_000;
  while (Date.now() < deadline) {
    try {
      if ((await readFile(output, "utf8")).includes(fragment)) return;
    } catch (error) {
      if (error.code !== "ENOENT") throw error;
    }
    await new Promise((resolve) => setTimeout(resolve, 50));
  }
  assert.fail(`watch output did not contain ${fragment}`);
}

import { defineConfig } from "@playwright/test";
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
const label = process.env.BENCH_LABEL ?? "local";
const baseURL = process.env.BENCH_URL;
if (!baseURL || !/^[a-z0-9-]+$/.test(label)) throw new Error("Set BENCH_URL and a simple BENCH_LABEL");
// Exactly the maintained consumer journeys, using a web-standard document identity for both frameworks.
let source = readFileSync(new URL("../../examples/todo-spring-boot/browser-tests/todos.spec.mjs", import.meta.url), "utf8");
const changes = [
  [`const epoch = await page.locator('meta[name="woge-page-epoch"]').getAttribute("content");`,
   `const epoch = await page.evaluate(() => performance.timeOrigin);\n  const createStarted = performance.now();`],
  [`await expect(page.locator('meta[name="woge-page-epoch"]')).toHaveAttribute("content", epoch);`,
   `expect(await page.evaluate(() => performance.timeOrigin)).toBe(epoch);`],
  [`await expect(page.locator('meta[name="woge-page-epoch"]')).not.toHaveAttribute("content", epoch);`,
   `expect(await page.evaluate(() => performance.timeOrigin)).not.toBe(epoch);`],
  [`await expect(row).toHaveCount(1);`,
   `await expect(row).toHaveCount(1);\n  testInfo.annotations.push({type: "interaction", description: JSON.stringify({name: "create-and-visible", ms: performance.now() - createStarted})});`],
];
for (const [before, after] of changes) {
  if (!source.includes(before)) throw new Error("The shared consumer test changed; review the benchmark adaptation");
  source = source.replace(before, after);
}
mkdirSync("build/browser-tests", { recursive: true });
writeFileSync("build/browser-tests/todos.spec.mjs", source);
export default defineConfig({
  testDir: "build/browser-tests", workers: 1, retries: 0, forbidOnly: true, timeout: 20_000,
  outputDir: `build/evidence/browser-traces-${label}`,
  reporter: [["line"], ["json", { outputFile: `build/evidence/browser-${label}.json` }]],
  use: { baseURL, trace: "retain-on-failure", screenshot: "only-on-failure" },
  projects: ["chromium", "firefox", "webkit"].flatMap(browserName => [
    { name: `${browserName}-enhanced`, use: { browserName, javaScriptEnabled: true } },
    { name: `${browserName}-native`, use: { browserName, javaScriptEnabled: false } },
  ]),
});

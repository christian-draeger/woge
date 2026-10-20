import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "failure-tests", workers: 1, retries: 0, forbidOnly: true,
  outputDir: "build/expected-failure",
  reporter: [["line"], ["json", { outputFile: "build/evidence/node-expected-failure.json" }]],
  use: { baseURL: process.env.WOGE_TODO_URL, browserName: "chromium", trace: "on", screenshot: "only-on-failure" },
});

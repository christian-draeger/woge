import { defineConfig } from "@playwright/test";

const browsers = ["chromium", "firefox", "webkit"];

export default defineConfig({
  testDir: "./reference-browser-tests",
  fullyParallel: true,
  forbidOnly: true,
  retries: 0,
  workers: process.env.CI ? 3 : undefined,
  outputDir: "test-results/reference-application",
  reporter: [
    ["line"],
    ["html", { open: "never", outputFolder: "playwright-report/reference-application" }],
  ],
  use: {
    baseURL: "http://127.0.0.1:8080",
    headless: true,
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
  },
  webServer: {
    command: "../../gradlew -p ../.. :woge-reference-spring-webflux:bootRun --console=plain",
    url: "http://127.0.0.1:8080/projects/woge",
    reuseExistingServer: false,
    timeout: 120_000,
    stdout: "pipe",
    stderr: "pipe",
  },
  projects: browsers.flatMap((browserName) => [
    {
      name: `${browserName}-enhanced`,
      testMatch: /enhanced\.spec\.mjs/,
      use: { browserName },
    },
    {
      name: `${browserName}-no-javascript`,
      testMatch: /no-javascript\.spec\.mjs/,
      use: { browserName, javaScriptEnabled: false },
    },
  ]),
});

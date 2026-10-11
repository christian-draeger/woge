import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./browser-tests",
  fullyParallel: true,
  forbidOnly: true,
  retries: 0,
  reporter: [["line"], ["html", { open: "never" }]],
  use: {
    baseURL: "http://127.0.0.1:4173",
    headless: true,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  webServer: {
    command: "node browser-tests/server.mjs",
    port: 4173,
    reuseExistingServer: false,
  },
  projects: ["chromium", "firefox", "webkit"].flatMap((browserName) => [
    {
      name: browserName,
      testIgnore: /no-javascript\.spec\.mjs/,
      use: { browserName },
    },
    {
      name: `${browserName}-no-javascript`,
      testMatch: /no-javascript\.spec\.mjs/,
      use: { browserName, javaScriptEnabled: false },
    },
  ]),
});

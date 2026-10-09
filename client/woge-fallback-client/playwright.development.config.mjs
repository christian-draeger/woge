import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./development-browser-tests",
  fullyParallel: false,
  workers: 1,
  forbidOnly: true,
  retries: 0,
  reporter: [["line"], ["html", { open: "never", outputFolder: "playwright-report/development" }]],
  use: { baseURL: "http://127.0.0.1:4273", headless: true },
  webServer: {
    command: "../../gradlew -p ../.. :woge-dev-browser:runBrowserFixture --console=plain -q",
    url: "http://127.0.0.1:4273",
    reuseExistingServer: false,
    timeout: 120000,
  },
  projects: [
    { name: "chromium", use: { browserName: "chromium" } },
    { name: "firefox", use: { browserName: "firefox" } },
    { name: "webkit", use: { browserName: "webkit" } },
  ],
});

import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  fullyParallel: false,
  workers: 1,
  forbidOnly: true,
  retries: 0,
  reporter: "line",
  use: { headless: true },
  projects: [
    {
      name: "chromium-stable",
      use: { browserName: "chromium" }
    },
    {
      name: "chromium-experimental-html-streaming",
      use: {
        browserName: "chromium",
        launchOptions: { args: ["--enable-experimental-web-platform-features"] }
      }
    }
  ]
});

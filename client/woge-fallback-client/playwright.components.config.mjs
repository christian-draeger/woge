import { defineConfig } from "@playwright/test";
import base from "./playwright.config.mjs";

// Needs fixtures from `./gradlew :woge-component-registry:componentBrowserTest`.
export default defineConfig({
  ...base,
  testMatch: /navigation-overlay-feedback\.spec\.mjs/,
  projects: ["chromium", "firefox", "webkit"].map((browserName) => ({ name: browserName, use: { browserName } })),
});

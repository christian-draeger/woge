import { defineConfig } from "@playwright/test";
import { copyFileSync, mkdirSync } from "node:fs";
mkdirSync(new URL("build/node-tests/", import.meta.url), { recursive: true });
copyFileSync(new URL("../../examples/todo-spring-boot/browser-tests/todos.spec.mjs", import.meta.url),
  new URL("build/node-tests/todos.spec.mjs", import.meta.url));
if (!process.env.WOGE_TODO_URL) throw new Error("Set WOGE_TODO_URL");
export default defineConfig({
  testDir: "build/node-tests", workers: 1, retries: 0, forbidOnly: true,
  reporter: [["line"], ["html", { outputFolder: "build/node-report", open: "never" }]],
  outputDir: "build/node-results",
  use: { baseURL: process.env.WOGE_TODO_URL, trace: "on", screenshot: "only-on-failure" },
  projects: ["chromium", "firefox", "webkit"].flatMap(browserName => [
    { name: `${browserName}-enhanced`, use: { browserName, javaScriptEnabled: true } },
    { name: `${browserName}-native`, use: { browserName, javaScriptEnabled: false } },
  ]),
});

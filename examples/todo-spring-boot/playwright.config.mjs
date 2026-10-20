import { defineConfig } from "@playwright/test";
import { createServer } from "node:net";

const port = Number(process.env.WOGE_TODO_BROWSER_PORT ?? await new Promise((resolve, reject) => {
  const server = createServer();
  server.on("error", reject);
  server.listen(0, "127.0.0.1", () => {
    const port = server.address().port;
    server.close((error) => error ? reject(error) : resolve(port));
  });
}));
process.env.WOGE_TODO_BROWSER_PORT = String(port);
const baseURL = process.env.WOGE_TODO_URL ?? `http://127.0.0.1:${port}`;

export default defineConfig({
  testDir: "./browser-tests",
  fullyParallel: false,
  workers: 1,
  forbidOnly: true,
  retries: 0,
  reporter: [["line"], ["html", { open: "never" }]],
  use: { baseURL, headless: true, trace: "retain-on-failure", screenshot: "only-on-failure" },
  webServer: process.env.WOGE_TODO_URL ? undefined : {
    command: "java -jar build/libs/woge-todo-0.1.0.jar",
    env: { ...process.env, PORT: String(port) },
    url: baseURL,
    timeout: 60_000,
    reuseExistingServer: false,
  },
  projects: ["chromium", "firefox", "webkit"].flatMap((browserName) => [
    { name: `${browserName}-enhanced`, use: { browserName, javaScriptEnabled: true } },
    { name: `${browserName}-native`, use: { browserName, javaScriptEnabled: false } },
  ]),
});

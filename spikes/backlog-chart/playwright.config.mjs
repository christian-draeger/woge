import { defineConfig } from "@playwright/test";

const origin = "http://127.0.0.1:4188";
export default defineConfig({
  testDir: "browser",
  use: { baseURL: origin, viewport: { width: 720, height: 480 } },
  webServer: {
    command: "./build/install/woge-backlog-chart-spike/bin/woge-backlog-chart-spike",
    env: { PORT: "4188" },
    url: `${origin}/backlog`,
  },
});

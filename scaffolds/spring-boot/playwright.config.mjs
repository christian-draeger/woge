import { defineConfig } from "@playwright/test";
import { createServer } from "node:net";

const wogeRepository = process.env.WOGE_REPOSITORY;
const springAdapter = process.env.WOGE_SPRING_ADAPTER ?? "webflux";

if (!wogeRepository) {
  throw new Error("WOGE_REPOSITORY must point to a local Maven repository");
}
if (!new Set(["webflux", "mvc"]).has(springAdapter)) {
  throw new Error(`Unknown WOGE_SPRING_ADAPTER '${springAdapter}'`);
}

const browserPort = Number(process.env.WOGE_BROWSER_PORT ?? (await findAvailablePort()));
if (!Number.isInteger(browserPort) || browserPort < 1 || browserPort > 65_535) {
  throw new Error("WOGE_BROWSER_PORT must be a valid TCP port");
}
process.env.WOGE_BROWSER_PORT = String(browserPort);
const browserUrl = `http://127.0.0.1:${browserPort}`;
const inheritedEnvironment = Object.fromEntries(
  Object.entries(process.env).filter(([, value]) => value !== undefined),
);

export default defineConfig({
  testDir: "./browser-tests",
  fullyParallel: false,
  forbidOnly: true,
  retries: 0,
  reporter: [["line"], ["html", { open: "never" }]],
  use: {
    baseURL: browserUrl,
    browserName: "chromium",
    headless: true,
    javaScriptEnabled: false,
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
  },
  webServer: {
    command: "./gradlew bootRun --console=plain",
    env: {
      ...inheritedEnvironment,
      ORG_GRADLE_PROJECT_wogeRepository: wogeRepository,
      ORG_GRADLE_PROJECT_wogeSpringAdapter: springAdapter,
      SERVER_PORT: String(browserPort),
    },
    url: `${browserUrl}/`,
    reuseExistingServer: false,
    timeout: 120_000,
    stdout: "pipe",
    stderr: "pipe",
  },
});

function findAvailablePort() {
  return new Promise((resolve, reject) => {
    const server = createServer();
    server.unref();
    server.on("error", reject);
    server.listen({ host: "127.0.0.1", port: 0 }, () => {
      const address = server.address();
      if (!address || typeof address === "string") {
        server.close();
        reject(new Error("Could not allocate a local browser-test port"));
        return;
      }
      server.close((error) => (error ? reject(error) : resolve(address.port)));
    });
  });
}

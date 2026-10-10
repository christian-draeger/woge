import { spawn, spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { mkdirSync, mkdtempSync, readdirSync, readFileSync, rmSync, statSync, writeFileSync } from "node:fs";
import { join, relative } from "node:path";
import { gzipSync } from "node:zlib";

const spike = import.meta.dirname;
const vite = join(spike, "node_modules/vite/bin/vite.js");
const iterations = Number(process.env.WOGE_VITE_ITERATIONS ?? 5);

const files = (directory) =>
  readdirSync(directory, { recursive: true, withFileTypes: true })
    .filter((entry) => entry.isFile())
    .map((entry) => join(entry.parentPath, entry.name))
    .sort();

const median = (values) => [...values].sort((a, b) => a - b)[Math.floor(values.length / 2)];
const summary = (values) => ({
  min: Math.round(Math.min(...values)),
  median: Math.round(median(values)),
  max: Math.round(Math.max(...values)),
});

function treeDigest(directory) {
  const hash = createHash("sha256");
  for (const file of files(directory)) {
    hash.update(relative(directory, file)).update("\0").update(readFileSync(file)).update("\0");
  }
  return hash.digest("hex");
}

function footprint() {
  const modules = join(spike, "node_modules");
  const bytes = files(modules).reduce((sum, file) => sum + statSync(file).size, 0);
  const lock = JSON.parse(readFileSync(join(spike, "package-lock.json"), "utf8"));
  const packages = Object.keys(lock.packages).filter((name) => name !== "").length;
  return { nodeModulesBytes: bytes, installedPackages: packages };
}

function build(environment) {
  mkdirSync(join(spike, "build"), { recursive: true });
  // Inside the project, as in a real build, so source-map paths stay project-relative.
  const outDir = mkdtempSync(join(spike, "build", "measure-"));
  const started = performance.now();
  const result = spawnSync(process.execPath, [vite, "build", "--config", "vite.config.mjs", "--logLevel", "error"], {
    cwd: spike,
    env: { ...process.env, VITE_OUT_DIR: outDir, ...environment },
    encoding: "utf8",
  });
  const elapsed = performance.now() - started;
  if (result.status !== 0) throw new Error(`vite build failed\n${result.stderr}`);
  return { outDir, elapsed };
}

function productionBuilds() {
  const times = [];
  const digests = new Set();
  let last;
  for (let index = 0; index < iterations; index += 1) {
    const { outDir, elapsed } = build({});
    times.push(elapsed);
    digests.add(treeDigest(outDir));
    if (last) rmSync(last, { recursive: true });
    last = outDir;
  }
  const outputs = Object.fromEntries(
    files(last).map((file) => {
      const content = readFileSync(file);
      return [relative(last, file), { bytes: content.length, gzip: gzipSync(content, { level: 9 }).length }];
    }),
  );
  const css = readFileSync(join(last, "main.css"), "utf8");
  const plainCssPreserved = css.includes(readFileSync(join(spike, "src/frontend/application.css"), "utf8"));
  const nestingLowered = css.includes(".card h2") && !css.includes("& h2");
  rmSync(last, { recursive: true });

  const hashed = build({ VITE_HASHED: "true", VITE_SOURCEMAP: "true" });
  const manifest = JSON.parse(readFileSync(join(hashed.outDir, ".vite/manifest.json"), "utf8"));
  const maps = files(hashed.outDir).filter((file) => file.endsWith(".map"));
  const absoluteMapSources = maps.some((file) =>
    JSON.parse(readFileSync(file, "utf8")).sources.some((source) => source.startsWith("/") || source.includes(spike)),
  );
  rmSync(hashed.outDir, { recursive: true });

  return {
    buildMilliseconds: summary(times),
    uniqueOutputDigests: digests.size,
    stableNameOutputs: outputs,
    plainCssPreserved,
    nestingLowered,
    hashedManifestEntries: Object.keys(manifest),
    sourceMapFiles: maps.length,
    absoluteMapSources,
  };
}

async function waitFor(url, deadline) {
  while (performance.now() < deadline) {
    try {
      const response = await fetch(url);
      if (response.ok) return response;
    } catch {
      // Server not listening yet.
    }
    await new Promise((resolve) => setTimeout(resolve, 10));
  }
  throw new Error(`Timed out waiting for ${url}`);
}

function rssKilobytes(pid) {
  const result = spawnSync("ps", ["-o", "rss=", "-p", String(pid)], { encoding: "utf8" });
  return Number(result.stdout.trim());
}

async function developmentServer() {
  const port = 5199;
  const origin = `http://localhost:${port}`;
  const started = performance.now();
  const child = spawn(process.execPath, [vite, "--config", "vite.config.mjs", "--logLevel", "error"], {
    cwd: spike,
    env: { ...process.env, VITE_PORT: String(port) },
    stdio: ["ignore", "ignore", "inherit"],
  });
  try {
    const deadline = performance.now() + 30_000;
    await waitFor(`${origin}/@vite/client`, deadline);
    const listening = performance.now() - started;
    // Loading the modules once builds Vite's module graph, as a browser page would.
    for (const path of ["/main.ts", "/application.css", "/tailwind.css", "/chart.ts"]) {
      await waitFor(`${origin}${path}`, deadline);
    }
    const firstModules = performance.now() - started;

    const socket = new WebSocket(`ws://localhost:${port}/`, "vite-hmr");
    const messages = [];
    let notify;
    socket.addEventListener("message", (event) => {
      const message = JSON.parse(event.data);
      if (message.type === "update" || message.type === "full-reload") {
        messages.push(message);
        notify?.(message);
      }
    });
    await new Promise((resolve, reject) => {
      socket.addEventListener("open", resolve, { once: true });
      socket.addEventListener("error", reject, { once: true });
    });

    const edits = {
      plainCss: ["src/frontend/application.css", (text, n) => text.replace(/98% [^ ]+/, `98% 0.0${n}`)],
      typeScript: ["src/frontend/chart.ts", (text, n) => text.replace(/chart-v\d+/, `chart-v${n}`)],
      tailwindCandidate: ["src/kotlin/TaskCard.kt", (text, n) => text.replace(/p-\d+"\n    false/, `p-${n}"\n    false`)],
    };
    const latency = {};
    const updateKinds = {};
    for (const [name, [file, edit]] of Object.entries(edits)) {
      const path = join(spike, file);
      const original = readFileSync(path, "utf8");
      const times = [];
      try {
        for (let index = 1; index <= iterations; index += 1) {
          const received = new Promise((resolve, reject) => {
            const timer = setTimeout(() => reject(new Error(`No HMR message for ${name}`)), 10_000);
            notify = (message) => {
              clearTimeout(timer);
              resolve(message);
            };
          });
          const written = performance.now();
          writeFileSync(path, edit(original, index + 1));
          const message = await received;
          times.push(performance.now() - written);
          updateKinds[name] = message.type === "update" ? [...new Set(message.updates.map((u) => u.type))] : ["full-reload"];
          await new Promise((resolve) => setTimeout(resolve, 150));
        }
      } finally {
        writeFileSync(path, original);
        await new Promise((resolve) => setTimeout(resolve, 300));
      }
      latency[name] = summary(times);
    }
    socket.close();
    const residentKilobytes = rssKilobytes(child.pid);
    const stopping = performance.now();
    const exited = new Promise((resolve) => child.once("exit", resolve));
    child.kill("SIGTERM");
    await exited;
    return {
      shutdownMilliseconds: Math.round(performance.now() - stopping),
      listeningMilliseconds: Math.round(listening),
      firstModulesMilliseconds: Math.round(firstModules),
      residentKilobytes,
      hmrLatencyMilliseconds: latency,
      hmrUpdateKinds: updateKinds,
    };
  } finally {
    if (child.exitCode === null && child.signalCode === null) child.kill("SIGTERM");
  }
}

const result = {
  recordedWith: {
    node: process.version,
    vite: JSON.parse(readFileSync(join(spike, "node_modules/vite/package.json"), "utf8")).version,
    tailwind: JSON.parse(readFileSync(join(spike, "node_modules/tailwindcss/package.json"), "utf8")).version,
    platform: `${process.platform}-${process.arch}`,
    iterations,
  },
  footprint: footprint(),
  production: productionBuilds(),
  development: await developmentServer(),
};
console.log(JSON.stringify(result, null, 2));

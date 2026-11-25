// Profile the real toggle endpoint of the unchanged Todo comparison image.
// Usage: node testing/performance/profile-todo-actions.mjs IMAGE OUTPUT_DIRECTORY [CANDIDATE_IMAGE]
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { createHash, randomUUID } from "node:crypto";
import { chmodSync, mkdirSync, readFileSync, statSync, writeFileSync } from "node:fs";
import http from "node:http";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { setTimeout } from "node:timers/promises";
import { isPatchStreamMediaType, PatchStreamDecoder, PATCH_STREAM_MEDIA_TYPE } from "../../client/woge-fallback-client/src/protocol.js";

const [image, directory, candidate] = process.argv.slice(2);
assert(image && directory, "Expected the local Todo comparison image and an output directory");
const output = resolve(directory);
const recordings = resolve(output, "recordings");
mkdirSync(recordings, { recursive: true });
chmodSync(recordings, 0o777); // The frozen image writes recordings as a non-root container user.

const root = fileURLToPath(new URL("../../", import.meta.url));
const baseFlags = "-Xms128m -Xmx512m -XX:+UseG1GC";
const plan = candidate ? [
  ["spring-woge", true, "before"], ["spring-woge", true, "after"],
  ["ktor-woge", true, "after"], ["ktor-woge", true, "before"],
  ["spring-woge", true, "after"], ["spring-woge", true, "before"],
  ["ktor-woge", true, "before"], ["ktor-woge", true, "after"],
] : [
  ["spring-woge", false], ["spring-woge", true],
  ["ktor-woge", true], ["ktor-woge", false],
  ["ktor-woge", false], ["ktor-woge", true],
  ["spring-woge", true], ["spring-woge", false],
];
const warmupActions = 240;
const measuredActions = 1200;
const docker = (...args) => execFileSync("docker", args, { encoding: "utf8", timeout: 30_000, maxBuffer: 10 * 1024 * 1024 }).trim();
const sha256 = (bytes) => createHash("sha256").update(bytes).digest("hex");
const actionPath = "/woge-actions/toggle-todo";
const contentType = "application/x-www-form-urlencoded";
const actionBody = "id=1&filter=all";

const fixture = [
  "spikes/todo-framework-comparison/src/main/kotlin/experiment/Bench.kt",
  "spikes/todo-framework-comparison/Dockerfile",
  "spikes/todo-framework-comparison/run.py",
  "examples/todo-spring-boot/src/main/kotlin/example/todo/TodoActions.kt",
  "examples/todo-spring-boot/src/main/kotlin/example/todo/TodoPage.kt",
  "examples/todo-spring-boot/src/main/kotlin/example/todo/Todos.kt",
];
const metadata = JSON.parse(docker("image", "inspect", image))[0];
const candidateMetadata = candidate && JSON.parse(docker("image", "inspect", candidate))[0];
if (candidateMetadata) {
  assert.notEqual(metadata.Id, candidateMetadata.Id);
  assert.equal(metadata.Architecture, candidateMetadata.Architecture);
  assert.equal(metadata.Os, candidateMetadata.Os);
}
const results = {
  revision: execFileSync("git", ["rev-parse", "HEAD"], { cwd: root, encoding: "utf8" }).trim(),
  sources: Object.fromEntries(fixture.map((path) => [path, sha256(readFileSync(resolve(root, path)))])),
  image: metadata.Id, architecture: metadata.Architecture, os: metadata.Os,
  ...(candidateMetadata && { candidateImage: candidateMetadata.Id }),
  driver: { platform: process.platform, architecture: process.arch, node: process.version },
  limits: { nanoCpus: 2_000_000_000, memoryBytes: 768 * 1024 * 1024, pidLimit: 256 },
  warmupActions, measuredActions, plan, runs: [],
};

function request(agent, port, method, path, body = "", headers = {}) {
  return new Promise((accept, reject) => {
    const request = http.request({
      agent, host: "127.0.0.1", port, method, path,
      headers: { ...headers, ...(body ? { "Content-Length": Buffer.byteLength(body) } : {}) },
    }, (response) => {
      const chunks = [];
      response.on("data", (chunk) => chunks.push(chunk));
      response.on("end", () => accept({
        status: response.statusCode, headers: response.headers, body: Buffer.concat(chunks),
      }));
      response.on("error", reject);
    });
    request.setTimeout(8000, () => request.destroy(new Error("HTTP request timed out")));
    request.on("error", reject);
    request.end(body);
  });
}

async function waitForPage(agent, port) {
  const deadline = performance.now() + 30_000;
  while (performance.now() < deadline) {
    try {
      const page = await request(agent, port, "GET", "/");
      if (page.status === 200) return page.body.toString("utf8");
    } catch {
      // The listener may not be ready yet.
    }
    await setTimeout(100);
  }
  throw new Error("Timed out waiting for a usable Todo page");
}

function stateOf(html) {
  const epoch = html.match(/<meta name="woge-page-epoch" content="([^"]+)">/)[1];
  const revisions = new Map(Array.from(
    html.matchAll(/data-woge-region="([^"]+)" data-woge-revision="([0-9]+)"/g),
    ([, id, revision]) => [id, BigInt(revision)],
  ));
  assert.equal(revisions.size, 3);
  assert.equal(Array.from(html.matchAll(/data-todo-id=/g)).length, 10);
  return { epoch, revisions, interaction: 0n, completed: completedOf(html) };
}

function completedOf(html) {
  const first = html.match(/<li data-todo-id="1"[^>]*>([\s\S]*?)<\/li>/)[1];
  assert(first.includes("Todo &lt;1&gt; &amp; Kotlin"));
  return first.includes(" (completed)");
}

function verifyAction(response, state) {
  assert.equal(response.status, 200);
  assert(isPatchStreamMediaType(response.headers["content-type"]));
  const decoder = new PatchStreamDecoder();
  const events = decoder.push(response.body);
  decoder.finish();
  assert.deepEqual(events.pop(), { type: "complete", patchCount: 3 });
  const seen = new Set();
  for (const event of events) {
    assert.equal(event.type, "patch");
    const patch = event.patch;
    assert.equal(patch.operation, "replace");
    assert.equal(patch.epoch, state.epoch);
    assert.equal(patch.interactionSequence, state.interaction);
    assert.equal(patch.baseRevision, state.revisions.get(patch.target));
    assert(!seen.has(patch.target));
    seen.add(patch.target);
    state.revisions.set(patch.target, patch.nextRevision);
  }
  assert.equal(seen.size, 3);
  const board = events.find(({ patch }) => patch.html.includes('data-todo-id="1"')).patch.html;
  assert.equal(Array.from(board.matchAll(/data-todo-id=/g)).length, 10);
  assert.equal(completedOf(board), !state.completed);
  assert(events.some(({ patch }) => patch.html.includes("Todo updated.")));
  assert(events.some(({ patch }) => patch.html.includes('id="add-todo"')));
  state.completed = !state.completed;
}

async function runAction(agent, port, state) {
  state.interaction += 1n;
  const headers = {
    Origin: `http://127.0.0.1:${port}`,
    "Content-Type": contentType, Accept: PATCH_STREAM_MEDIA_TYPE,
    "Woge-Request-Identity": randomUUID(), "Woge-Page-Epoch": state.epoch,
    "Woge-Interaction": state.interaction.toString(),
    "Woge-Region-Revisions": [...state.revisions].map(([id, revision]) => `${id}:${revision}`).join(","),
  };
  const started = performance.now();
  const response = await request(agent, port, "POST", actionPath, actionBody, headers);
  const latencyMs = performance.now() - started;
  verifyAction(response, state);
  return { latencyMs, responseBytes: response.body.length, completed: state.completed };
}

async function checkNativeAndRejected(agent, port) {
  const headers = { Origin: `http://127.0.0.1:${port}`, "Content-Type": contentType };
  assert.equal((await request(agent, port, "POST", actionPath, actionBody,
    { ...headers, Origin: "https://other.example" })).status, 403);
  const invalid = await request(agent, port, "POST", "/woge-actions/add-todo", "title=+++&filter=all", headers);
  assert.equal(invalid.status, 400);
  assert.match(invalid.body.toString("utf8"), /name="title" id="todo-title"[^>]*value="   "/);
  for (const completed of [true, false]) {
    const action = await request(agent, port, "POST", actionPath, actionBody, headers);
    assert.equal(action.status, 303);
    assert.equal(completedOf((await request(agent, port, "GET", "/")).body.toString("utf8")), completed);
  }
}

function processState(name) {
  assert.equal(docker("exec", name, "cat", "/proc/1/comm"), "java");
  const status = docker("exec", name, "cat", "/proc/1/status");
  const cpu = docker("exec", name, "cat", "/sys/fs/cgroup/cpu.stat");
  return {
    rssKiB: Number(status.match(/^VmRSS:\s+(\d+)/m)[1]),
    cpuUsageMicros: Number(cpu.match(/^usage_usec (\d+)/m)[1]),
  };
}

for (const [index, [mode, profile, variant]] of plan.entries()) {
  const label = `${index + 1}-${mode}-${variant ?? (profile ? "jfr" : "control")}`;
  const name = `woge-action-${randomUUID().slice(0, 12)}`;
  const agent = new http.Agent({ keepAlive: true, maxSockets: 1 });
  const flags = profile ? `${baseFlags} -XX:StartFlightRecording=settings=profile,filename=/recordings/${label}.jfr,dumponexit=true,maxsize=32m` : baseFlags;
  const args = [
    "run", "--detach", "--rm", "--name", name, "--cpus", "2", "--memory", "768m",
    "--pids-limit", "256", "--read-only", "--tmpfs", "/tmp:rw,nosuid,size=64m",
    "-p", "127.0.0.1::8080", "-e", `BENCH_MODE=${mode}`, "-e", `JAVA_OPTS=${flags}`,
  ];
  if (profile) args.push("--mount", `type=bind,src=${recordings},dst=/recordings`);
  docker(...args, variant === "after" ? candidate : image);
  try {
    const settings = JSON.parse(docker("inspect", name))[0].HostConfig;
    assert.equal(settings.NanoCpus, results.limits.nanoCpus);
    assert.equal(settings.Memory, results.limits.memoryBytes);
    assert.equal(settings.PidsLimit, results.limits.pidLimit);
    const port = Number(docker("port", name, "8080/tcp").split(":").at(-1));
    const initial = await waitForPage(agent, port);
    assert.equal(stateOf(initial).completed, false);
    const ready = processState(name);
    await checkNativeAndRejected(agent, port);
    const state = stateOf((await request(agent, port, "GET", "/")).body.toString("utf8"));
    const warmup = [];
    const samples = [];
    for (let count = 0; count < warmupActions; count++) warmup.push(await runAction(agent, port, state));
    const before = processState(name);
    const measuredStart = new Date().toISOString();
    for (let count = 0; count < measuredActions; count++) samples.push(await runAction(agent, port, state));
    const measuredEnd = new Date().toISOString();
    const after = processState(name);
    assert.equal(completedOf((await request(agent, port, "GET", "/")).body.toString("utf8")), false);
    console.log(`${label}: ${samples.length} verified actions, ${before.rssKiB} → ${after.rssKiB} KiB RSS`);
    results.runs.push({ label, mode, profile, variant, flags, ready, before, after, measuredStart, measuredEnd, warmup, samples });
  } catch (failure) {
    results.failure = { label, message: failure.message };
    throw failure;
  } finally {
    writeFileSync(resolve(output, "raw.json"), JSON.stringify(results, null, 2) + "\n");
    agent.destroy();
    try {
      writeFileSync(resolve(output, `${label}.log`), docker("logs", name));
    } finally {
      docker("stop", "--timeout", "10", name);
    }
  }
  if (profile) assert(statSync(resolve(recordings, `${label}.jfr`)).size > 0);
}

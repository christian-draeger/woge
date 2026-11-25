// Usage: node testing/performance/summarize-todo-actions.mjs OUTPUT_DIRECTORY
import { execFileSync } from "node:child_process";
import { readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { gzipSync } from "node:zlib";

const [directory] = process.argv.slice(2);
if (!directory) throw new Error("Expected output directory from profile-todo-actions.mjs");
const output = resolve(directory);
const java = process.env.JAVA_HOME ? resolve(process.env.JAVA_HOME, "bin/java") : "java";
const extractor = fileURLToPath(new URL("./ActionProfileEvents.java", import.meta.url));
const raw = JSON.parse(readFileSync(resolve(output, "raw.json"), "utf8"));
const appFrames = [
  ["escaping", /^dev\.woge\.html\.internal\.HtmlEscaping/],
  ["HTML rendering", /^(dev\.woge\.html\.|dev\.woge\.protocol\.(HtmlFrame|PatchHtml))/],
  ["frames", /^(dev\.woge\.runtime\.(EncodedPatch|.*PatchStream)|dev\.woge\.protocol\..*PatchStream|dev\.woge\.host\.PatchStream)/],
  ["identity", /^dev\.woge\.(host|protocol)\.(RenderSession|RenderIdentity|RegionIdentity|PageIdentity|PatchTarget|.*RequestIdentity)/],
  ["typed form decode", /^dev\.woge\.host\.(FormDecoder|UrlEncoded|.*FormBody|FormDecod)/],
  ["Woge host bridge", /^dev\.woge\.(ktor|spring\.webflux)\./],
  ["Todo application", /^example\.todo\./],
];

function classify(frames) {
  // URL checks called by the fragment validator belong to protocol safety work.
  if (frames.some((frame) => /^dev\.woge\.protocol\.internal\.PatchHtmlPolicyKt#/.test(frame))) return "patch HTML safety";
  for (const frame of frames) {
    const match = appFrames.find(([, pattern]) => pattern.test(frame));
    if (match) return match[0];
  }
  if (frames.some((frame) => /^(io\.ktor|io\.netty|reactor\.netty|org\.springframework)\./.test(frame))) {
    return "HTTP/Netty framework";
  }
  return "other or unattributed";
}

function percentile(ordered, quantile) {
  return ordered[Math.ceil(ordered.length * quantile) - 1];
}

for (const run of raw.runs) {
  if (run.profile) {
    const recording = resolve(output, "recordings", `${run.label}.jfr`);
    const rows = execFileSync(java, [extractor, recording, run.measuredStart, run.measuredEnd], {
      encoding: "utf8", maxBuffer: 16 * 1024 * 1024,
    });
    run.profileEvents = rows.trimEnd().split("\n").filter(Boolean).map((line) => {
      const [type, observed, value, name, stack = ""] = line.split("\t");
      return { type, observed, value: Number(value), name, frames: stack ? stack.split(";") : [] };
    });
  }
  const ordered = run.samples.map(({ latencyMs }) => latencyMs).sort((a, b) => a - b);
  run.summary = {
    p50Ms: percentile(ordered, 0.5),
    p95Ms: percentile(ordered, 0.95),
    p99Ms: percentile(ordered, 0.99),
    responseBytes: [...new Set(run.samples.map(({ responseBytes }) => responseBytes))].sort((a, b) => a - b),
    cpuMicros: run.after.cpuUsageMicros - run.before.cpuUsageMicros,
  };
  if (run.profileEvents) {
    const stages = {};
    const allocations = run.profileEvents.filter(({ type }) => type === "alloc");
    const collections = run.profileEvents.filter(({ type }) => type === "gc");
    for (const { frames, value } of allocations) {
      const group = classify(frames);
      const bucket = stages[group] ??= { samples: 0, weight: 0 };
      bucket.samples += 1;
      bucket.weight += value;
    }
    run.summary.allocations = { samples: allocations.length, stages };
    run.summary.collections = { events: collections.length, nanos: collections.reduce((sum, { value }) => sum + value, 0) };
  }
  console.log(run.label, run.summary, `RSS ${run.ready.rssKiB}/${run.before.rssKiB}/${run.after.rssKiB} KiB`);
}

writeFileSync(resolve(output, "action-profile.json.gz"), gzipSync(JSON.stringify(raw), { level: 9 }));

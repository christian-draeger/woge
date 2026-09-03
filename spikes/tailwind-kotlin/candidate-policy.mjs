import { readFile, readdir, stat } from "node:fs/promises";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";

const utilityPrefix = "(?:[a-z0-9-]+:)*(?:bg|text|border|ring|outline|shadow|p[trblxy]?|m[trblxy]?|w|h|size|grid-cols|col-span)-";
const interpolatedUtility = new RegExp(`"[^"\\n]*${utilityPrefix}[^"\\n]*\\$\\{?`, "g");
const concatenatedUtility = new RegExp(`"[^"\\n]*${utilityPrefix}"\\s*\\+`, "g");

export async function findDynamicUtilities(paths) {
  const findings = [];
  for (const path of await kotlinFiles(paths)) {
    const source = await readFile(path, "utf8");
    for (const pattern of [interpolatedUtility, concatenatedUtility]) {
      pattern.lastIndex = 0;
      for (const match of source.matchAll(pattern)) {
        const line = source.slice(0, match.index).split("\n").length;
        findings.push({ path, line, text: match[0] });
      }
    }
  }
  return findings;
}

async function kotlinFiles(paths) {
  const files = [];
  for (const input of paths) {
    const path = resolve(input);
    const details = await stat(path);
    if (details.isFile()) {
      if (path.endsWith(".kt")) files.push(path);
      continue;
    }
    for (const entry of await readdir(path, { withFileTypes: true })) {
      files.push(...await kotlinFiles([resolve(path, entry.name)]));
    }
  }
  return files.sort();
}

async function main() {
  const findings = await findDynamicUtilities(process.argv.slice(2));
  if (findings.length === 0) return;
  for (const finding of findings) {
    console.error(`${finding.path}:${finding.line}: dynamic Tailwind utility name: ${finding.text}`);
  }
  console.error("Map runtime choices to complete static class tokens or add an explicit @source inline() candidate.");
  process.exitCode = 1;
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) await main();

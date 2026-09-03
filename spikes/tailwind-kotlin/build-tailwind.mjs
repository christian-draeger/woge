import { spawn } from "node:child_process";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { basename, dirname, resolve } from "node:path";
import { pathToFileURL } from "node:url";

const sourceMapPattern = /\/\*# sourceMappingURL=data:application\/json;base64,([A-Za-z0-9+/=]+) \*\//;

export async function buildTailwind({ input, output, minify = false, executable }) {
  const resolvedOutput = resolve(output);
  await mkdir(dirname(resolvedOutput), { recursive: true });
  const command = executable ?? process.execPath;
  const arguments_ = executable
    ? ["--input", input, "--output", output, "--map", ...(minify ? ["--minify"] : [])]
    : [
        "node_modules/@tailwindcss/cli/dist/index.mjs",
        "--input", input,
        "--output", output,
        "--map",
        ...(minify ? ["--minify"] : [])
      ];

  await run(command, arguments_);
  const css = await readFile(resolvedOutput, "utf8");
  const match = css.match(sourceMapPattern);
  if (!match) throw new Error("Tailwind did not emit the requested inline source map");

  const map = JSON.parse(Buffer.from(match[1], "base64").toString("utf8"));
  map.sources = map.sources.map(normalizeSource);
  const mapName = `${basename(resolvedOutput)}.map`;
  await writeFile(`${resolvedOutput}.map`, `${JSON.stringify(map)}\n`);
  await writeFile(resolvedOutput, css.replace(sourceMapPattern, `/*# sourceMappingURL=${mapName} */`));
}

function normalizeSource(source) {
  const projectPrefix = `${process.cwd().replace(/^\/+/, "")}/`;
  const projectRelative = source.startsWith(projectPrefix) ? source.slice(projectPrefix.length) : source;
  return projectRelative.startsWith("node_modules/") ? projectRelative.slice("node_modules/".length) : projectRelative;
}

function run(command, arguments_) {
  return new Promise((resolvePromise, reject) => {
    const child = spawn(command, arguments_, { cwd: process.cwd(), stdio: "inherit" });
    child.once("error", reject);
    child.once("exit", (code, signal) => {
      if (code === 0) resolvePromise();
      else reject(new Error(`${command} exited with ${signal ?? code}`));
    });
  });
}

async function main() {
  const arguments_ = process.argv.slice(2);
  const value = (name) => {
    const index = arguments_.indexOf(name);
    if (index < 0 || !arguments_[index + 1]) throw new Error(`Missing ${name}`);
    return arguments_[index + 1];
  };
  await buildTailwind({
    input: value("--input"),
    output: value("--output"),
    minify: arguments_.includes("--minify"),
    executable: arguments_.includes("--executable") ? value("--executable") : undefined
  });
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) await main();

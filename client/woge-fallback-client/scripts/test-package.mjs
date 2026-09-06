import { createHash } from "node:crypto";
import { execFile } from "node:child_process";
import { mkdir, readFile, readdir, rm, writeFile } from "node:fs/promises";
import { promisify } from "node:util";
import { build } from "esbuild";

const execute = promisify(execFile);
const packageDirectory = new URL("../", import.meta.url);
const buildDirectory = new URL("../build/", import.meta.url);
const firstPackDirectory = new URL("pack-a/", buildDirectory);
const secondPackDirectory = new URL("pack-b/", buildDirectory);
const consumerDirectory = new URL("package-consumer/", buildDirectory);
const npmCacheDirectory = new URL("npm-cache/", buildDirectory);

await Promise.all(
  [firstPackDirectory, secondPackDirectory, consumerDirectory, npmCacheDirectory].map(async (directory) => {
    await rm(directory, { recursive: true, force: true });
    await mkdir(directory, { recursive: true });
  }),
);

const firstTarball = await pack(firstPackDirectory);
const secondTarball = await pack(secondPackDirectory);
const firstHash = await sha256(firstTarball);
const secondHash = await sha256(secondTarball);
if (firstHash !== secondHash) throw new Error("Two clean fallback-client packs produced different tarballs");

const { stdout: archiveOutput } = await execute("tar", ["-tzf", firstTarball.pathname]);
const archiveFiles = archiveOutput.trim().split("\n").sort();
const expectedFiles = [
  "package/README.md",
  "package/dist/LICENSE",
  "package/dist/index.d.ts",
  "package/dist/manifest.json",
  "package/dist/woge-fallback.js",
  "package/dist/woge-fallback.js.map",
  "package/package.json",
].sort();
if (JSON.stringify(archiveFiles) !== JSON.stringify(expectedFiles)) {
  throw new Error(`Unexpected fallback-client package contents:\n${archiveFiles.join("\n")}`);
}

await writeFile(
  new URL("package.json", consumerDirectory),
  `${JSON.stringify({ name: "woge-external-consumer", private: true, type: "module" }, null, 2)}\n`,
);
await execute(
  "npm",
  [
    "install",
    "--ignore-scripts",
    "--no-audit",
    "--no-fund",
    "--cache",
    npmCacheDirectory.pathname,
    firstTarball.pathname,
  ],
  { cwd: consumerDirectory.pathname },
);

const entryFile = new URL("entry.js", consumerDirectory);
await writeFile(
  entryFile,
  [
    'import { createWogePatchRuntime, WOGE_PATCH_PROTOCOL_VERSION } from "@woge/fallback-client";',
    "if (WOGE_PATCH_PROTOCOL_VERSION !== 1) throw new Error(\"Installed protocol version mismatch\");",
    "globalThis.WogePackageConsumer = { createWogePatchRuntime, protocolVersion: WOGE_PATCH_PROTOCOL_VERSION };",
    "",
  ].join("\n"),
);
await execute("node", [entryFile.pathname], { cwd: consumerDirectory.pathname });
await build({
  absWorkingDir: consumerDirectory.pathname,
  entryPoints: [entryFile.pathname],
  outfile: new URL("consumer-bundle.js", consumerDirectory).pathname,
  bundle: true,
  format: "esm",
  target: "es2022",
  sourcemap: "external",
});

const installedPackage = JSON.parse(
  await readFile(new URL("node_modules/@woge/fallback-client/package.json", consumerDirectory), "utf8"),
);
const installedManifest = JSON.parse(
  await readFile(new URL("node_modules/@woge/fallback-client/dist/manifest.json", consumerDirectory), "utf8"),
);
if (installedPackage.version !== installedManifest.packageVersion) {
  throw new Error("Installed package and asset manifest versions differ");
}
await writeFile(
  new URL("package-test-result.json", buildDirectory),
  `${JSON.stringify(
    {
      package: `${installedPackage.name}@${installedPackage.version}`,
      patchProtocolVersion: installedManifest.patchProtocolVersion,
      tarballSha256: firstHash,
      archiveFiles,
    },
    null,
    2,
  )}\n`,
);

console.log(
  `[woge-package] installed=${installedPackage.name}@${installedPackage.version} ` +
    `protocol=${installedManifest.patchProtocolVersion} tarball_sha256=${firstHash}`,
);

async function pack(destination) {
  await execute(
    "npm",
    [
      "pack",
      "--silent",
      "--cache",
      npmCacheDirectory.pathname,
      "--pack-destination",
      destination.pathname,
    ],
    { cwd: packageDirectory.pathname },
  );
  const archives = (await readdir(destination)).filter((name) => name.endsWith(".tgz"));
  if (archives.length !== 1) throw new Error("Expected npm pack to produce exactly one tarball");
  return new URL(archives[0], destination);
}

async function sha256(file) {
  return createHash("sha256").update(await readFile(file)).digest("hex");
}

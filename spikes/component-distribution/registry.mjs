#!/usr/bin/env node

import { createHash } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, isAbsolute, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const lockDirectory = ".woge-components";

function sha256(content) {
  return createHash("sha256").update(content).digest("hex");
}

function assertSafeRelativePath(path, label) {
  if (typeof path !== "string" || path.length === 0 || isAbsolute(path) || path.split(/[\\/]/).includes("..")) {
    throw new Error(`${label} must be a relative path without parent traversal: ${path}`);
  }
}

export async function loadManifest(manifestPath) {
  const absolutePath = resolve(manifestPath);
  const manifest = JSON.parse(await readFile(absolutePath, "utf8"));
  if (manifest.schemaVersion !== 1 || !manifest.name || !manifest.version || !manifest.license) {
    throw new Error(`Unsupported or incomplete component manifest: ${absolutePath}`);
  }
  if (!manifest.provenance?.repository || !manifest.provenance?.revision || !manifest.provenance?.path) {
    throw new Error(`Component manifest lacks immutable provenance: ${absolutePath}`);
  }
  if (!Array.isArray(manifest.files) || manifest.files.length === 0) {
    throw new Error(`Component manifest has no distributed files: ${absolutePath}`);
  }
  const targets = new Set();
  for (const file of manifest.files) {
    assertSafeRelativePath(file.source, "Manifest source");
    assertSafeRelativePath(file.target, "Manifest target");
    if (!/^[0-9a-f]{64}$/.test(file.sha256)) throw new Error(`Invalid SHA-256 for ${file.source}`);
    if (targets.has(file.target)) throw new Error(`Duplicate manifest target: ${file.target}`);
    targets.add(file.target);
  }
  for (const candidate of manifest.tailwind?.candidateFiles ?? []) {
    assertSafeRelativePath(candidate, "Tailwind candidate");
    if (!manifest.files.some((file) => file.source === candidate)) {
      throw new Error(`Tailwind candidate is not a distributed source: ${candidate}`);
    }
  }
  return { absolutePath, directory: dirname(absolutePath), manifest };
}

export async function verifyManifest(manifestPath) {
  const loaded = await loadManifest(manifestPath);
  for (const file of loaded.manifest.files) {
    const content = await readFile(resolve(loaded.directory, file.source));
    const actual = sha256(content);
    if (actual !== file.sha256) {
      throw new Error(`${file.source}: expected ${file.sha256}, got ${actual}`);
    }
  }
  return loaded;
}

function lockPath(targetDirectory, componentName) {
  return resolve(targetDirectory, lockDirectory, `${componentName}.json`);
}

export async function install(manifestPath, targetDirectory) {
  const loaded = await verifyManifest(manifestPath);
  const files = [];
  for (const file of loaded.manifest.files) {
    const source = await readFile(resolve(loaded.directory, file.source));
    const target = resolve(targetDirectory, file.target);
    try {
      const existing = await readFile(target);
      if (sha256(existing) !== file.sha256) {
        throw new Error(`Refusing to overwrite locally owned file: ${file.target}`);
      }
    } catch (error) {
      if (error.code !== "ENOENT") throw error;
    }
    await mkdir(dirname(target), { recursive: true });
    await writeFile(target, source);
    files.push({ target: file.target, installedSha256: file.sha256, kind: file.kind });
  }

  const lock = {
    schemaVersion: 1,
    name: loaded.manifest.name,
    version: loaded.manifest.version,
    license: loaded.manifest.license,
    provenance: loaded.manifest.provenance,
    manifestSha256: sha256(await readFile(loaded.absolutePath)),
    files,
  };
  const destination = lockPath(targetDirectory, loaded.manifest.name);
  await mkdir(dirname(destination), { recursive: true });
  await writeFile(destination, `${JSON.stringify(lock, null, 2)}\n`, "utf8");
  return lock;
}

export async function inspect(targetDirectory, componentName) {
  const lock = JSON.parse(await readFile(lockPath(targetDirectory, componentName), "utf8"));
  const files = [];
  for (const file of lock.files) {
    assertSafeRelativePath(file.target, "Lock target");
    try {
      const currentSha256 = sha256(await readFile(resolve(targetDirectory, file.target)));
      files.push({ ...file, currentSha256, state: currentSha256 === file.installedSha256 ? "clean" : "modified" });
    } catch (error) {
      if (error.code !== "ENOENT") throw error;
      files.push({ ...file, currentSha256: null, state: "missing" });
    }
  }
  return { lock, files };
}

export async function planUpdate(targetDirectory, componentName, nextManifestPath) {
  const current = await inspect(targetDirectory, componentName);
  const next = await verifyManifest(nextManifestPath);
  if (next.manifest.name !== componentName) {
    throw new Error(`Cannot update ${componentName} from manifest for ${next.manifest.name}`);
  }
  const installedByTarget = new Map(current.files.map((file) => [file.target, file]));
  const files = next.manifest.files.map((file) => {
    const installed = installedByTarget.get(file.target);
    if (!installed) return { target: file.target, action: "add" };
    if (installed.currentSha256 === installed.installedSha256) {
      return { target: file.target, action: file.sha256 === installed.installedSha256 ? "unchanged" : "replace-safe" };
    }
    return {
      target: file.target,
      action: file.sha256 === installed.installedSha256 ? "preserve-local" : "merge-required",
    };
  });
  return { from: current.lock.version, to: next.manifest.version, files };
}

async function runCli() {
  const [command, manifestOrTarget, targetOrName, maybeManifest] = process.argv.slice(2);
  let result;
  if (command === "verify") result = (await verifyManifest(manifestOrTarget)).manifest;
  else if (command === "install") result = await install(manifestOrTarget, targetOrName);
  else if (command === "check") result = await inspect(manifestOrTarget, targetOrName);
  else if (command === "plan-update") result = await planUpdate(manifestOrTarget, targetOrName, maybeManifest);
  else throw new Error("Usage: registry.mjs verify MANIFEST | install MANIFEST TARGET | check TARGET NAME | plan-update TARGET NAME MANIFEST");
  process.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  runCli().catch((error) => {
    process.stderr.write(`${error.message}\n`);
    process.exitCode = 1;
  });
}

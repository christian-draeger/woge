import assert from "node:assert/strict";
import { mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { resolve } from "node:path";
import test from "node:test";
import { inspect, install, planUpdate, verifyManifest } from "../registry.mjs";

const root = resolve(import.meta.dirname, "..");
const manifestV1 = resolve(root, "registry/project-board/0.1.0/manifest.json");
const manifestV2 = resolve(root, "registry/project-board/0.2.0/manifest.json");

test("manifests pin provenance, license, compatibility, candidate roots, and file hashes", async () => {
  for (const manifestPath of [manifestV1, manifestV2]) {
    const { manifest } = await verifyManifest(manifestPath);
    assert.equal(manifest.license, "Apache-2.0");
    assert.match(manifest.provenance.repository, /^https:\/\/github\.com\//);
    assert.equal(manifest.compatibility.requiresHydration, false);
    assert.deepEqual(manifest.tailwind.candidateFiles, ["files/ProjectBoardRecipe.kt"]);
  }
});

test("install is deterministic and records the exact upstream sources", async (t) => {
  const target = await mkdtemp(resolve(tmpdir(), "woge-registry-"));
  t.after(() => rm(target, { recursive: true, force: true }));

  await install(manifestV1, target);
  const firstLock = await readFile(resolve(target, ".woge-components/project-board.json"), "utf8");
  await install(manifestV1, target);
  const secondLock = await readFile(resolve(target, ".woge-components/project-board.json"), "utf8");
  assert.equal(secondLock, firstLock);
  assert.ok((await inspect(target, "project-board")).files.every((file) => file.state === "clean"));
});

test("local ownership is visible and updates never overwrite ambiguous edits", async (t) => {
  const target = await mkdtemp(resolve(tmpdir(), "woge-registry-update-"));
  t.after(() => rm(target, { recursive: true, force: true }));

  await install(manifestV1, target);
  const kotlinTarget = resolve(target, "src/main/kotlin/dev/woge/ui/registry/projectboard/ProjectBoardRecipe.kt");
  const customized = (await readFile(kotlinTarget, "utf8")).replace("Portfolio", "Aurora portfolio");
  await writeFile(kotlinTarget, customized, "utf8");

  await assert.rejects(
    install(manifestV1, target),
    /Refusing to overwrite locally owned file/,
  );
  assert.equal(await readFile(kotlinTarget, "utf8"), customized);

  const state = await inspect(target, "project-board");
  assert.equal(state.files.find((file) => file.kind === "kotlin-source").state, "modified");

  const update = await planUpdate(target, "project-board", manifestV2);
  assert.deepEqual(update.files, [
    {
      target: "src/main/kotlin/dev/woge/ui/registry/projectboard/ProjectBoardRecipe.kt",
      action: "merge-required",
    },
    { target: "src/main/resources/project-board-recipe.css", action: "replace-safe" },
  ]);
});

test("the checked-in consumer remains an explicit local customization", async () => {
  const result = await inspect(resolve(root, "consumer"), "project-board");
  assert.ok(result.files.every((file) => file.state === "modified"));
});

test("manifest paths cannot escape the target application", async (t) => {
  const fixture = await mkdtemp(resolve(tmpdir(), "woge-registry-hostile-"));
  t.after(() => rm(fixture, { recursive: true, force: true }));
  await mkdir(resolve(fixture, "files"));
  await writeFile(resolve(fixture, "files/component.kt"), "package fixture\n", "utf8");
  await writeFile(
    resolve(fixture, "manifest.json"),
    JSON.stringify({
      schemaVersion: 1,
      name: "hostile",
      version: "1.0.0",
      license: "Apache-2.0",
      provenance: { repository: "https://example.test/repo", revision: "deadbeef", path: "registry/hostile" },
      files: [{
        source: "files/component.kt",
        target: "../../outside.kt",
        sha256: "0".repeat(64),
        kind: "kotlin-source",
      }],
    }),
    "utf8",
  );

  await assert.rejects(verifyManifest(resolve(fixture, "manifest.json")), /without parent traversal/);
});

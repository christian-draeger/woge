import { readFile, stat } from "node:fs/promises";
import { resolve } from "node:path";
import { gzipSync } from "node:zlib";

const root = import.meta.dirname;
const artifacts = [
  ["headless binary JAR", "headless-primitives/build/libs/headless-primitives-0.1.0.jar"],
  ["styled binary JAR", "binary-components/build/libs/binary-components-0.1.0.jar"],
  ["styled binary CSS", "binary-components/src/main/resources/META-INF/woge/project-board.css"],
  ["source registry Kotlin", "registry/project-board/0.1.0/files/ProjectBoardRecipe.kt"],
  ["source registry CSS", "registry/project-board/0.1.0/files/project-board.css"],
  ["customized source Kotlin", "consumer/src/main/kotlin/dev/woge/ui/registry/projectboard/ProjectBoardRecipe.kt"],
  ["customized source CSS", "consumer/src/main/resources/project-board-recipe.css"],
  ["hybrid recipe Kotlin", "consumer/src/main/kotlin/dev/woge/ui/consumer/HybridProjectBoard.kt"],
  ["hybrid recipe CSS", "consumer/src/main/resources/hybrid-project-board.css"],
];

const measurements = [];
for (const [name, relativePath] of artifacts) {
  const absolutePath = resolve(root, relativePath);
  const content = await readFile(absolutePath);
  measurements.push({
    name,
    path: relativePath,
    bytes: (await stat(absolutePath)).size,
    gzipBytes: gzipSync(content, { level: 9, mtime: 0 }).byteLength,
    lines: relativePath.endsWith(".kt") || relativePath.endsWith(".css")
      ? content.toString("utf8").trimEnd().split("\n").length
      : null,
  });
}

process.stdout.write(`${JSON.stringify({ schemaVersion: 1, measurements }, null, 2)}\n`);

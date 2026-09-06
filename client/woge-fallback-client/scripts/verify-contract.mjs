import { readFile } from "node:fs/promises";
import { WOGE_PATCH_PROTOCOL_VERSION } from "../src/version.js";

const packageMetadata = JSON.parse(await readFile(new URL("../package.json", import.meta.url), "utf8"));
const manifest = JSON.parse(await readFile(new URL("../dist/manifest.json", import.meta.url), "utf8"));
const kotlinPatch = await readFile(
  new URL("../../../modules/woge-protocol/src/main/kotlin/dev/woge/protocol/Patch.kt", import.meta.url),
  "utf8",
);
const kotlinPatchStream = await readFile(
  new URL("../../../modules/woge-protocol/src/main/kotlin/dev/woge/protocol/PatchStreamValues.kt", import.meta.url),
  "utf8",
);

const versions = {
  package: packageMetadata.woge?.patchProtocolVersion,
  browser: WOGE_PATCH_PROTOCOL_VERSION,
  manifest: manifest.patchProtocolVersion,
  patchIr: matchVersion(kotlinPatch, /CURRENT: PatchProtocolVersion = PatchProtocolVersion\((\d+)\)/u, "Patch IR"),
  patchStream: matchVersion(kotlinPatchStream, /const val VERSION: Int = (\d+)/u, "patch stream"),
};

if (new Set(Object.values(versions)).size !== 1) {
  throw new Error(`Woge patch protocol versions differ: ${JSON.stringify(versions)}`);
}
if (packageMetadata.dependencies && Object.keys(packageMetadata.dependencies).length > 0) {
  throw new Error("The fallback client must not have runtime package dependencies");
}
if (manifest.runtimeDependencies.length > 0 || manifest.cssFiles.length > 0) {
  throw new Error("The fallback client manifest must not declare a framework or CSS runtime");
}
if (manifest.packageVersion !== packageMetadata.version) {
  throw new Error("Fallback-client manifest and package versions differ");
}

console.log(
  `[woge-package] package=${packageMetadata.name}@${packageMetadata.version} protocol=${WOGE_PATCH_PROTOCOL_VERSION}`,
);

function matchVersion(content, pattern, source) {
  const value = Number(pattern.exec(content)?.[1]);
  if (!Number.isSafeInteger(value) || value <= 0) throw new Error(`Could not read protocol version from ${source}`);
  return value;
}

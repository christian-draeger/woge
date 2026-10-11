import { createHash } from "node:crypto";
import { copyFile, mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { build } from "esbuild";
import { WOGE_PATCH_PROTOCOL_VERSION } from "../src/version.js";

const distDirectory = new URL("../dist/", import.meta.url);
const moduleFile = new URL("woge-fallback.js", distDirectory);
const sourceMapFile = new URL("woge-fallback.js.map", distDirectory);
const declarationFile = new URL("index.d.ts", distDirectory);
const packageMetadata = JSON.parse(await readFile(new URL("../package.json", import.meta.url), "utf8"));

await rm(distDirectory, { recursive: true, force: true });
await mkdir(distDirectory, { recursive: true });

await build({
  entryPoints: [new URL("../src/index.js", import.meta.url).pathname],
  outfile: moduleFile.pathname,
  bundle: true,
  external: ["./transactions.js"],
  format: "esm",
  target: "es2022",
  minify: true,
  legalComments: "none",
  sourcemap: "external",
});

await build({
  entryPoints: [new URL("../src/transactions.js", import.meta.url).pathname],
  outfile: new URL("transactions.js", distDirectory).pathname,
  bundle: true, format: "esm", target: "es2022", minify: true, legalComments: "none",
});

// Navigation is a separate opt-in entry; shared state primitives keep the core graph unchanged.
await build({
  entryPoints: [new URL("../src/navigation.js", import.meta.url).pathname],
  outfile: new URL("woge-navigation.js", distDirectory).pathname,
  bundle: true, external: ["./state.js"], format: "esm", target: "es2022",
  minify: true, legalComments: "none",
});
await build({
  entryPoints: [new URL("../src/state.js", import.meta.url).pathname],
  outfile: new URL("state.js", distDirectory).pathname,
  bundle: true, format: "esm", target: "es2022", minify: true, legalComments: "none",
});
await build({
  entryPoints: [new URL("../src/islands.js", import.meta.url).pathname],
  outfile: new URL("islands.js", distDirectory).pathname,
  bundle: true, format: "esm", target: "es2022", minify: true, legalComments: "none",
});
await build({
  entryPoints: [new URL("../src/signals.js", import.meta.url).pathname],
  outfile: new URL("signals.js", distDirectory).pathname,
  bundle: true, format: "esm", target: "es2022", minify: true, legalComments: "none",
});
await build({
  entryPoints: [new URL("../src/websocket.js", import.meta.url).pathname],
  outfile: new URL("websocket.js", distDirectory).pathname,
  bundle: true, format: "esm", target: "es2022", minify: true, legalComments: "none",
});
await build({
  entryPoints: [new URL("../src/dpu.js", import.meta.url).pathname],
  outfile: new URL("dpu.js", distDirectory).pathname,
  bundle: true, external: ["./transactions.js"], format: "esm", target: "es2022", minify: true, legalComments: "none",
});
for (const name of ["dpu.d.ts", "websocket.d.ts", "navigation.d.ts", "islands.d.ts", "signals.d.ts"]) {
  await copyFile(new URL(`../src/${name}`, import.meta.url), new URL(name, distDirectory));
}
await copyFile(new URL("../src/index.d.ts", import.meta.url), declarationFile);
await copyFile(new URL("../../../LICENSE", import.meta.url), new URL("LICENSE", distDirectory));

const files = {};
for (const [name, url] of [
  ["module", moduleFile],
  ["sourceMap", sourceMapFile],
  ["types", declarationFile],
  ...["dpu.js", "dpu.d.ts", "websocket.js", "websocket.d.ts", "woge-navigation.js", "navigation.d.ts", "state.js", "transactions.js", "islands.js", "islands.d.ts", "signals.js", "signals.d.ts"]
    .map((name) => [name, new URL(name, distDirectory)]),
]) {
  const bytes = await readFile(url);
  files[name] = Object.freeze({
    path: url.pathname.split("/").at(-1),
    bytes: bytes.byteLength,
    sha256: createHash("sha256").update(bytes).digest("hex"),
  });
}

const moduleBytes = await readFile(moduleFile);
const manifest = {
  schemaVersion: 1,
  packageVersion: packageMetadata.version,
  patchProtocolVersion: WOGE_PATCH_PROTOCOL_VERSION,
  format: "es-module",
  target: "es2022",
  runtimeDependencies: [],
  cssFiles: [],
  integrity: `sha256-${createHash("sha256").update(moduleBytes).digest("base64")}`,
  files,
};
await writeFile(new URL("manifest.json", distDirectory), `${JSON.stringify(manifest, null, 2)}\n`);

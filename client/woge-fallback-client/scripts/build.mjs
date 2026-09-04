import { copyFile, mkdir, rm } from "node:fs/promises";
import { build } from "esbuild";

await rm(new URL("../dist/", import.meta.url), { recursive: true, force: true });
await mkdir(new URL("../dist/", import.meta.url), { recursive: true });

await build({
  entryPoints: [new URL("../src/index.js", import.meta.url).pathname],
  outfile: new URL("../dist/woge-fallback.js", import.meta.url).pathname,
  bundle: true,
  format: "esm",
  target: "es2022",
  minify: true,
  legalComments: "none",
  sourcemap: false,
});

await copyFile(new URL("../src/index.d.ts", import.meta.url), new URL("../dist/index.d.ts", import.meta.url));

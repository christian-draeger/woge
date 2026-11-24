import { createHash } from "node:crypto";
import { copyFile, mkdir, writeFile } from "node:fs/promises";
import { build } from "esbuild";

const output = "build/island-assets/static/assets/chart";
const { outputFiles: [bundle] } = await build({
  entryPoints: ["widget/chart.js"],
  bundle: true,
  external: ["/assets/woge/islands.js"],
  format: "esm",
  target: "es2022",
  minify: true,
  legalComments: "eof",
  write: false,
});
await mkdir(`${output}/licenses`, { recursive: true });
await writeFile(`${output}/backlog.js`, bundle.contents);
await writeFile(`${output}/backlog.version`, createHash("sha256").update(bundle.contents).digest("hex"));
await copyFile("node_modules/chart.js/LICENSE.md", `${output}/licenses/chart.js.txt`);
await copyFile("node_modules/@kurkle/color/LICENSE", `${output}/licenses/kurkle-color.txt`);

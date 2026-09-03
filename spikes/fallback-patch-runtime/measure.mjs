import { readFile } from "node:fs/promises";
import { brotliCompressSync, gzipSync } from "node:zlib";

const source = await readFile(new URL("./runtime.js", import.meta.url));

console.log(`runtime_source_bytes=${source.byteLength}`);
console.log(`runtime_gzip_bytes=${gzipSync(source, { level: 9 }).byteLength}`);
console.log(`runtime_brotli_bytes=${brotliCompressSync(source).byteLength}`);

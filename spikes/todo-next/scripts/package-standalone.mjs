import { cp, mkdir } from "node:fs/promises";

await mkdir(new URL("../.next/standalone/.next/", import.meta.url), { recursive: true });
await Promise.all([
    cp(new URL("../.next/static/", import.meta.url), new URL("../.next/standalone/.next/static/", import.meta.url),
        { recursive: true }),
    cp(new URL("../public/", import.meta.url), new URL("../.next/standalone/public/", import.meta.url),
        { recursive: true }),
]);

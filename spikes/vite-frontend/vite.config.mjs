import tailwindcss from "@tailwindcss/vite";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vite";

// Woge's asset tree (ADR 0068) owns content hashing, so Vite emits stable names
// under one directory and keeps relative chunk imports.
export default defineConfig({
  root: "src/frontend",
  base: "./",
  plugins: [tailwindcss()],
  server: {
    port: Number(process.env.VITE_PORT ?? 5173),
    strictPort: true,
    cors: { origin: process.env.WOGE_ORIGIN ?? "http://localhost:8080" },
    origin: `http://localhost:${process.env.VITE_PORT ?? 5173}`,
  },
  build: {
    outDir: process.env.VITE_OUT_DIR ?? "../../build/vite",
    emptyOutDir: true,
    manifest: process.env.VITE_HASHED === "true",
    sourcemap: process.env.VITE_SOURCEMAP === "true",
    rollupOptions: {
      input: { main: fileURLToPath(new URL("src/frontend/main.ts", import.meta.url)) },
      output: process.env.VITE_HASHED === "true"
        ? {}
        : {
            entryFileNames: "[name].js",
            chunkFileNames: "chunks/[name].js",
            assetFileNames: "[name][extname]",
          },
    },
  },
});

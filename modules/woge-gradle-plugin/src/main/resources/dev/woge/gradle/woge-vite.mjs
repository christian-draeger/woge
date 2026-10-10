// Runs the project's own Vite through its JavaScript API. Woge passes this script to
// `node --input-type=module --eval`, so `vite` resolves from the project's node_modules.
// Woge owns names and paths (ADR 0070); everything else comes from the project's vite.config.*.
import path from "node:path";

const env = process.env;
let vite;
try {
  vite = await import("vite");
} catch (error) {
  console.error(`Vite is not installed in ${process.cwd()}. Run \`npm install --save-dev vite\` (or \`npm ci\`).`);
  process.exit(1);
}

const root = env.WOGE_VITE_ROOT;
const shared = {
  configFile: env.WOGE_VITE_CONFIG || false,
  root,
  base: "./",
  publicDir: false,
  clearScreen: false,
};

if (env.WOGE_VITE_COMMAND === "build") {
  const input = Object.fromEntries(
    env.WOGE_VITE_ENTRIES.split(",").map((entry) => [entry.replace(/\.[^.]+$/, ""), path.join(root, entry)]),
  );
  await vite.build({
    ...shared,
    mode: "production",
    logLevel: "warn",
    build: {
      outDir: env.WOGE_VITE_OUT_DIR,
      emptyOutDir: true,
      manifest: false,
      sourcemap: env.WOGE_VITE_SOURCEMAP === "true",
      rollupOptions: {
        input,
        // Stable names and relative chunk imports: Woge's asset tree adds the content hash (ADR 0068).
        output: {
          entryFileNames: "[name].js",
          chunkFileNames: "chunks/[name].js",
          assetFileNames: "[name][extname]",
          // Source maps name files relative to the frontend folder, never by machine-specific paths.
          sourcemapPathTransform: (source, map) =>
            path.relative(root, path.resolve(path.dirname(map), source)).split(path.sep).join("/"),
        },
      },
    },
  });
} else {
  const port = Number(env.WOGE_VITE_PORT);
  const origin = `http://127.0.0.1:${port}`;
  const server = await vite.createServer({
    ...shared,
    mode: "development",
    server: {
      host: "127.0.0.1",
      port,
      strictPort: true,
      origin,
      cors: { origin: env.WOGE_APP_ORIGINS.split(",") },
    },
  });
  await server.listen();
  console.log(`Vite dev server ready at ${origin}`);
  const stop = async () => {
    await server.close();
    process.exit(0);
  };
  process.on("SIGTERM", stop);
  process.on("SIGINT", stop);
}

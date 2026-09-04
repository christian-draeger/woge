import { createServer } from "node:http";
import { readFile } from "node:fs/promises";

const routes = new Map([
  ["/", { path: new URL("./fixture.html", import.meta.url), type: "text/html; charset=utf-8" }],
  ["/woge-fallback.js", { path: new URL("../dist/woge-fallback.js", import.meta.url), type: "text/javascript; charset=utf-8" }],
]);

const server = createServer(async (request, response) => {
  const route = routes.get(new URL(request.url, "http://127.0.0.1").pathname);
  if (!route) {
    response.writeHead(404).end();
    return;
  }
  try {
    response.writeHead(200, { "content-type": route.type, "cache-control": "no-store" });
    response.end(await readFile(route.path));
  } catch {
    response.writeHead(500).end();
  }
});

server.listen(4173, "127.0.0.1");
process.on("SIGTERM", () => server.close());

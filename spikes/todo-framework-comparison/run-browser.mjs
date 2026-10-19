import { createServer, request as upstreamRequest } from "node:http";
import { spawn } from "node:child_process";
const upstream = new URL(process.env.BENCH_URL);
const proxy = createServer((incoming, outgoing) => {
  const forwarded = upstreamRequest(new URL(incoming.url, upstream), { method: incoming.method, headers: incoming.headers }, response => {
    outgoing.writeHead(response.statusCode, response.headers);
    response.pipe(outgoing);
    outgoing.on("close", () => response.destroy());
  });
  forwarded.on("error", () => { if (!outgoing.headersSent) outgoing.writeHead(502); outgoing.end(); });
  incoming.on("aborted", () => forwarded.destroy());
  incoming.pipe(forwarded);
});
await new Promise(resolve => proxy.listen(0, "127.0.0.1", resolve));
const child = spawn("node_modules/.bin/playwright", ["test", "--config=browser.config.mjs"], {
  stdio: "inherit", env: { ...process.env, BENCH_URL: `http://127.0.0.1:${proxy.address().port}` },
});
child.on("exit", code => { proxy.closeAllConnections(); proxy.close(); process.exitCode = code ?? 1; });

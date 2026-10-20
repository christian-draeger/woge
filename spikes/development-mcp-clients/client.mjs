import { readFileSync } from "node:fs";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StreamableHTTPClientTransport } from "@modelcontextprotocol/sdk/client/streamableHttp.js";
const connection = JSON.parse(readFileSync(process.argv[2], "utf8"));
const requestedProtocol = process.argv[3];
if (requestedProtocol && !["2025-03-26", "2025-06-18"].includes(requestedProtocol)) throw new Error("Unsupported test protocol");
// Override only the outgoing initialize offer; SDK transport, parsing and negotiation stay intact.
class VersionPinnedClient extends Client {
  request(request, ...rest) {
    if (requestedProtocol && request.method === "initialize") request = { ...request, params: { ...request.params, protocolVersion: requestedProtocol } };
    return super.request(request, ...rest);
  }
}
const client = new VersionPinnedClient({ name: "woge-interop-typescript", version: "1" });
const transport = new StreamableHTTPClientTransport(new URL(connection.url), {
  requestInit: { headers: connection.headers },
});
try {
  await client.connect(transport);
  if (requestedProtocol && transport.protocolVersion !== requestedProtocol) throw new Error("Requested version was not selected");
  const tools = (await client.listTools()).tools;
  if (tools.length !== 7) throw new Error(`Expected seven tools, got ${tools.length}`);
  const results = {};
  for (const name of ["status", "get_diagnostics", "get_manifest", "get_dev_urls"]) {
    const result = await client.callTool({ name, arguments: {} });
    if (result.isError) throw new Error(`${name} failed`);
    results[name] = result.structuredContent ?? JSON.parse(result.content[0].text);
  }
  const state = results.status;
  const wait = await client.callTool({ name: "await_build", arguments: { afterBuild: state.latestRequestedBuild, timeoutSeconds: 0.2 } });
  results.await_build = wait.structuredContent ?? JSON.parse(wait.content[0].text);
  if (results.await_build.result !== "timedOut") throw new Error("Expected bounded idle wait");
  console.log(JSON.stringify({ client: "typescript-sdk-1.32.1", requestedProtocol: requestedProtocol ?? "SDK default", protocol: transport.protocolVersion, tools: tools.map(t => t.name), phase: state.phase, manifest: !!results.get_manifest.manifest, idleWait: results.await_build.result }));
} finally {
  await client.close();
}

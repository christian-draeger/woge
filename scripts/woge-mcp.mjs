#!/usr/bin/env node
// Calls one tool of a running `wogeDev --mcp` session, like a coding agent would.
// Usage: node woge-mcp.mjs PATH/TO/build/woge-dev/mcp.json TOOL [JSON_ARGUMENTS]
// Prints the tool's structured result as JSON. Exits 3 when the tool reports an error.
import { readFileSync } from "node:fs";

const [connectionFile, tool, rawArguments = "{}"] = process.argv.slice(2);
if (!connectionFile || !tool) {
  console.error("Usage: woge-mcp.mjs MCP_JSON TOOL [JSON_ARGUMENTS]");
  process.exit(2);
}
const connection = JSON.parse(readFileSync(connectionFile, "utf8"));
let id = 0;

async function rpc(method, params) {
  const response = await fetch(connection.url, {
    method: "POST",
    headers: { ...connection.headers, "Content-Type": "application/json", Accept: "application/json, text/event-stream" },
    body: JSON.stringify({ jsonrpc: "2.0", id: ++id, method, params }),
  });
  if (!response.ok) throw new Error(`HTTP ${response.status}: ${await response.text()}`);
  const message = await response.json();
  if (message.error) throw new Error(`${method}: ${message.error.message}`);
  return message.result;
}

await rpc("initialize", { protocolVersion: "2025-06-18", capabilities: {}, clientInfo: { name: "woge-smoke", version: "1" } });
const result = await rpc("tools/call", { name: tool, arguments: JSON.parse(rawArguments) });
console.log(JSON.stringify(result.structuredContent));
process.exit(result.isError ? 3 : 0);

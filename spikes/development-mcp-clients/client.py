#!/usr/bin/env python3
import asyncio
import json
import pathlib
import sys
from importlib.metadata import version
import httpx2
from mcp import ClientSession
from mcp import types
from mcp.client.streamable_http import streamable_http_client

requested_protocol = sys.argv[2] if len(sys.argv) > 2 else None
assert requested_protocol in (None, "2025-03-26", "2025-06-18")

class VersionPinnedSession(ClientSession):
    async def send_request(self, request, *args, **kwargs):
        # Override the initialize offer only; retain the actual SDK parser/transport/negotiation.
        if requested_protocol and isinstance(request, types.InitializeRequest):
            request = request.model_copy(update={"params": request.params.model_copy(update={"protocol_version": requested_protocol})})
        return await super().send_request(request, *args, **kwargs)

async def main():
    connection = json.loads(pathlib.Path(sys.argv[1]).read_text())
    async with httpx2.AsyncClient(headers=connection["headers"]) as http:
        async with streamable_http_client(connection["url"], http_client=http) as transport:
            async with VersionPinnedSession(transport[0], transport[1]) as client:
                initialized = await client.initialize()
                assert not requested_protocol or initialized.protocol_version == requested_protocol
                tools = await client.list_tools()
                assert len(tools.tools) == 7
                results = {}
                for name in ("status", "get_diagnostics", "get_manifest", "get_dev_urls"):
                    result = await client.call_tool(name, {})
                    assert not result.is_error, name
                    results[name] = result.structured_content or json.loads(result.content[0].text)
                state = results["status"]
                wait = await client.call_tool("await_build", {"afterBuild": state["latestRequestedBuild"], "timeoutSeconds": 0.2})
                value = wait.structured_content or json.loads(wait.content[0].text)
                assert value["result"] == "timedOut"
                print(json.dumps({"client": "python-sdk-" + version("mcp"), "requestedProtocol": requested_protocol or "SDK default", "protocol": initialized.protocol_version,
                                  "tools": [tool.name for tool in tools.tools], "phase": state["phase"],
                                  "manifest": bool(results["get_manifest"]["manifest"]), "idleWait": value["result"]}))

asyncio.run(main())

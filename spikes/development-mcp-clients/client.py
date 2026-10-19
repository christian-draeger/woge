#!/usr/bin/env python3
import asyncio
import json
import pathlib
import sys
from importlib.metadata import version
import httpx2
from mcp import ClientSession
from mcp.client.streamable_http import streamable_http_client

async def main():
    connection = json.loads(pathlib.Path(sys.argv[1]).read_text())
    async with httpx2.AsyncClient(headers=connection["headers"]) as http:
        async with streamable_http_client(connection["url"], http_client=http) as transport:
            async with ClientSession(transport[0], transport[1]) as client:
                initialized = await client.initialize()
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
                print(json.dumps({"client": "python-sdk-" + version("mcp"), "protocol": initialized.protocol_version,
                                  "tools": [tool.name for tool in tools.tools], "phase": state["phase"],
                                  "manifest": bool(results["get_manifest"]["manifest"]), "idleWait": value["result"]}))

asyncio.run(main())

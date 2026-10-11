# Let coding agents follow `wogeDev` (experimental MCP)

`./gradlew wogeDev` rebuilds and restarts your app on every save. People watch the terminal and the
browser. A coding agent can do better: with `--mcp`, Woge offers a small local
[MCP](https://modelcontextprotocol.io) endpoint. It tells the agent when a build finished, which file
and line failed, and when the new version answers requests.

> Experimental. The tools may change. Nothing runs unless you pass `--mcp`.

## Start it

```bash
./gradlew wogeDev --mcp
```

The terminal shows:

```text
[woge] Experimental MCP endpoint for coding agents: http://127.0.0.1:52817/mcp (URL and token in build/woge-dev/mcp.json)
```

`build/woge-dev/mcp.json` is a ready-made MCP server entry:

```json
{"type": "http", "url": "http://127.0.0.1:52817/mcp", "headers": {"Authorization": "Bearer 3f9c..."}}
```

Copy it into your agent's MCP configuration, or let the agent read the file. The port and token are
new for every session. Use `--mcp-port=PORT` for a fixed port; the token still changes.

## Tools

| Tool | What it does |
| --- | --- |
| `status` | Current phase (`READY`, `BUILDING`, `BUILD_FAILED`, ...), build IDs, server generation |
| `await_build` | Waits for the next build after `afterBuild` and, by default, until the app serves it |
| `get_diagnostics` | Compiler and startup errors with code, file, line and summary |
| `reload` | Asks open browser tabs to reload |
| `restart` | Starts a fresh application process |
| `get_manifest` | The [application manifest](application-manifest.md) of the last successful build |
| `get_dev_urls` | The URLs to open in a browser |

## The agent loop

1. Call `status` and remember `latestRequestedBuild`.
2. Edit files.
3. Call `await_build` with `afterBuild` set to that number.
4. `result: "succeeded"`: open the URL from `get_dev_urls` with your browser tool and check the page.
   `result: "failed"`: read `build.diagnostics`, fix the file and line, and go back to step 1.

While a build fails, the last working version keeps serving. `reload` and `restart` refuse an
outdated `buildId` with `STALE_BUILD`, so an agent never acts on an old build by mistake.

Clicking, typing and screenshots are not Woge tools. Use the agent's own browser tool.

## Security

- The endpoint listens on `127.0.0.1` only and needs the bearer token from `mcp.json`.
- `mcp.json` is readable only by you and is deleted when `wogeDev` stops. The token is never printed.
- Requests from web pages (an `Origin` other than localhost) and unknown `Host` headers are rejected.
- Tools expose build state, short diagnostics, the manifest and URLs. They never return source files,
  raw Gradle output or data from requests to your app.

## Requirements

`--mcp` also runs `wogeManifest` after each build, so the app needs a host adapter for the manifest.
The Spring Boot scaffold has one already; see the [manifest guide](application-manifest.md) for
other setups.

The design and its limits are recorded in
[ADR 0075](../adr/0075-experimental-development-mcp-endpoint.md).

# ADR 0075: Offer an experimental, opt-in MCP endpoint in `wogeDev`

- Status: Accepted
- Date: 2026-10-13
- Decision owners: @christian-draeger
- Related issues: [#151](https://github.com/christian-draeger/woge/issues/151)
- Builds on: [ADR 0041](0041-orchestrator-owned-spring-reload-and-sse-channel.md),
  [ADR 0042](0042-single-actor-development-orchestrator.md),
  [ADR 0045](0045-wogedev-gradle-launcher-and-development-head-hook.md)

## Context

Coding agents use the same `wogeDev` loop as people: edit, wait for the build, look at the page. Today
an agent can only read the terminal or poll the page. That is slow and fragile: it cannot tell an old
page from a new one, and compiler errors arrive as free text.

`woge-dev-model` already defines typed development capabilities: status, wait for a build,
diagnostics, reload, restart, manifest and URLs. The Model Context Protocol (MCP) is the common way
for agents to call such tools. Issue #151 asked whether a thin MCP adapter over these capabilities
really helps, and what it would cost in security and maintenance.

## Decision

1. **Opt-in only.** `./gradlew wogeDev --mcp` starts an MCP endpoint next to the session. Without the
   flag nothing listens. The module `woge-dev-mcp` is internal tooling, depends only on
   `woge-dev-model` and has no stability guarantee.
2. **Thin adapter, no own logic.** Seven tools map 1:1 to `WogeDevelopmentCapabilities`: `status`,
   `await_build`, `get_diagnostics`, `reload`, `restart`, `get_manifest`, `get_dev_urls`. Build IDs
   and server generations are the same monotone IDs as in the browser channel (ADR 0041), so an old
   `buildId` is refused with `STALE_BUILD`.
3. **Transport.** MCP "Streamable HTTP" with plain JSON responses: `POST /mcp`, JSON-RPC 2.0, no SSE
   stream and no MCP session ID. The endpoint belongs to the `wogeDev` session, not to the app, so it
   survives app restarts.
4. **Security.** The server listens on `127.0.0.1` only. Every request needs a random bearer token.
   Woge writes URL and token to `build/woge-dev/mcp.json` (owner-only, deleted on exit) and never
   prints the token. Host and Origin checks block DNS rebinding and web pages; bodies are limited to
   1 MiB.
5. **Small exposure.** Tools return build state, redacted diagnostics (code, file, line, summary),
   the manifest of the last successful build and the dev URLs. No raw Gradle output, no source
   files, no request data from the running app.
6. **Browser actions stay with the agent.** Clicking, typing, screenshots and accessibility checks use
   the agent's own browser tool on the URL from `get_dev_urls`.
7. **Restart is always a fresh process.** Spring DevTools only restarts when classes changed, and an
   explicit restart has none, so `restart` uses the cold-restart path (ADR 0041 fallback) instead of
   waiting for a fast restart that never comes.

## Spike result

The scaffold dev smoke test (`scripts/test-spring-boot-scaffold-dev.sh`) now runs with `--mcp` and
drives the edit loop through the endpoint: status, manifest, edit then `await_build`, a compile error
with file and line, the fix, a refused stale reload and a restart to a newer generation. With MCP, an
agent knows the moment the new version serves requests and gets structured errors. Without it, the
agent must parse the terminal and guess when to reload. The cost is one small module (about 700
lines) on top of existing capabilities, so we keep it as an experimental feature.

## Alternatives considered

- **Parse the terminal.** Works for people, but agents get free text, no build IDs and no reliable
  "ready" signal. Kept as the default, not as the agent API.
- **MCP over stdio.** The agent would have to start `wogeDev` itself and own its lifecycle. A local
  HTTP endpoint lets a person and an agent share one running session.
- **Browser automation inside Woge.** Duplicates what agents already have. Rejected.
- **A stable public API now.** Too early: the tool set needs real agent use first.

## Consequences

- `--mcp` also builds `wogeManifest` after each build, so `get_manifest` matches the last successful
  build. Projects without a configured host adapter fail `wogeManifest` and therefore `--mcp` builds.
- The port is random by default (`--mcp-port` fixes it). Agents read `mcp.json` after each start.
- Tests: protocol and HTTP security tests in `woge-dev-mcp`, plus the end-to-end scaffold smoke test.

## Follow-up

- Compare agents with and without MCP across model families as part of the live evaluations in
  [#93](https://github.com/christian-draeger/woge/issues/93), not in PR CI.
- Decide on a stable port or token location once agent tools support project-local MCP config well.
- Promote, change or remove the tools after real use; until then the module stays experimental.

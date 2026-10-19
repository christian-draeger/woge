# Development MCP client interoperability

State: Frozen. [ADR 0113](../../docs/adr/0113-retain-experimental-development-mcp.md), issue #273.
Time box: two independent official SDK clients and the existing full development lifecycle gate.
No model calls, source dumps or additional Woge tools are part of this experiment.

Start the [Todo consumer](../../examples/todo-spring-boot/README.md) with `./gradlew wogeDev --mcp`.
From the repository root:

```shell
npm --prefix spikes/development-mcp-clients ci --ignore-scripts
node spikes/development-mcp-clients/client.mjs examples/todo-spring-boot/build/woge-dev/mcp.json
python3 -m venv spikes/development-mcp-clients/.venv
spikes/development-mcp-clients/.venv/bin/pip install -r spikes/development-mcp-clients/requirements.txt
spikes/development-mcp-clients/.venv/bin/python spikes/development-mcp-clients/client.py examples/todo-spring-boot/build/woge-dev/mcp.json
./gradlew scaffoldDevSmoke
```

The scripts read the session token in memory and print only a compact result. Never commit the
connection file. Stop `wogeDev` normally and verify the file was removed. The CLI fallback remains
`node scripts/woge-mcp.mjs PATH status`, ordinary `./gradlew check` and terminal/compiler output.

## Evidence, 2026-10-20

| Client | Transport | Negotiated version | Executed |
| --- | --- | --- | --- |
| Official TypeScript SDK 1.32.1 | Streamable HTTP / JSON | 2025-06-18 | initialize, 7-tool list, status, diagnostics, manifest, URLs, bounded idle await |
| Official Python SDK 2.3.0, Python 3.14.7 | Streamable HTTP / JSON | 2025-06-18 | Same operations |
| Maintained native smoke CLI | Streamable HTTP / JSON | Explicit 2025-06-18 | Actual build/ready, compilation failure/file+line, fix, stale reload, reload/restart, shutdown/token cleanup |

`./gradlew scaffoldDevSmoke` passed on WebFlux, MVC and Ktor (2m48s, 190 tasks). Existing protocol
tests cover exact 2025-03-26 selection and fallback for an unsupported requested version; that older
version has not been separately exercised through both SDK clients. Unit/server tests cover tokens,
Origin/Host, size limits and the seven tool schemas. The production artifact verifier remains in
the consumer build and production container gate.

The [current specification](https://modelcontextprotocol.io/specification/2026-07-28) is newer than
Woge's two negotiated versions. Both tested clients accept its 2025-06-18 fallback. This demonstrates
compatibility for these versions and operations, not conformance to every 2026 capability. Keep
supported versions explicit instead of advertising an untested latest version. Required initialize/
tool metadata was accepted by both SDK parsers; optional capabilities/icons/new features stay absent.

Controlled with/without-MCP model-family trials remain unrun. The human controls belong to #181.
No productivity, time-saving or model-comparison claim follows from protocol-client interoperability.

# ADR 0113: Retain the experimental development MCP boundary

- Status: Accepted
- Date: 2026-10-20
- Decision owners: @christian-draeger
- Related issues: [#273](https://github.com/christian-draeger/woge/issues/273), [#272](https://github.com/christian-draeger/woge/issues/272)

## Context

Woge already exposes seven opt-in development tools through WogeDevelopmentCapabilities. Two
independent official SDK clients and the full three-host lifecycle gate now provide
[reproducible interoperability evidence](../../spikes/development-mcp-clients/README.md).
They do not measure model productivity or establish a stable public contract.

## Decision

Retain the current incubating seven-tool surface and supported 2025-06-18/2025-03-26 negotiation.
Do not claim 2026-07-28 conformance or add tools without a measured consumer gap. Keep localhost,
token/Origin/Host checks, redacted diagnostics, per-session cleanup and production exclusion.
Terminal/build commands remain the supported fallback. Document the tested SDK matrix.

## Alternatives considered

Promoting on successful SDK handshakes would confuse transport compatibility with agent usefulness.
Replacing the existing capability boundary or adding shell/browser executors would expand trust
without evidence. Removing the integration would discard a tested bounded build-feedback path.

## Consequences

No new public API or protocol version is promised. Experimental changes still require explicit
versioned documentation and compatible tests; incompatible promotions need a new ADR and migration
policy. With/without-MCP model trials and human controls remain open before any stability or
comparative-productivity decision.

## Follow-up

Run controlled with/without-MCP trials through #273/#272 and #181's human control. Extend negotiated
versions only with actual client/conformance evidence and a documented compatibility policy.

## Subsequent evidence, 2026-10-20

The [explicit SDK matrix](../../spikes/development-mcp-clients/evidence/2026-10-20-protocol-matrix.json)
now covers both clients against both supported negotiated versions. Only the outgoing initialize
offer is pinned; SDK result validation and transport remain intact. The integration stays incubating:
these successful protocol operations do not replace the controlled model-family/human trials.

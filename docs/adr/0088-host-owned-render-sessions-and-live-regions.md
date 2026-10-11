# ADR 0088: Host-owned render sessions and one-binding live regions

- Status: Accepted
- Date: 2026-10-11
- Decision owners: Woge maintainers
- Related issues: [#215](https://github.com/christian-draeger/woge/issues/215), [#148](https://github.com/christian-draeger/woge/issues/148)
- Builds on: [ADR 0010](0010-identity-epochs-and-revisions.md), [ADR 0069](0069-live-invalidations-over-sse.md)

## Context

The complexity gradient made applications construct identity objects, load module-level secrets and
copy epochs and revisions into commands. One live region needed three route declarations and an
application-supplied JavaScript loader. These are presentation concerns, not domain inputs.

## Decision

Hosts install a fresh `RenderSession` in `RequestContext.render`. A session generates a random epoch,
addresses typed descriptors and writes epoch metadata. Deferred requests explicitly resume their
opaque epoch with the host secret. `RenderUpdates` resolves descriptors and current revisions inside
the existing prepared action builder. The session metadata opts into automatic action setup and ordering headers; legacy documents keep
their explicit client setup and ordering behavior. Managed documents share one browser region registry
for live and action updates. The shipped action client sends ordering headers, so command
classes contain only application fields. The bounded header parser rejects malformed state before
application execution. State remains presentation-only: it grants no authorization or CSRF exemption.

Spring Boot owns one `RenderIdentitySecret` bean sourced from `woge.render-identity.secret`. The Ktor
configuration constructor reads the same key; manual factories accept the secret as an option. Values
are Base64-encoded random bytes, at least 32 bytes. Invalid configured values fail startup. If unset,
generate a random startup secret and warn in configured hosts without logging it. A warning, rather
than environment/profile guessing, fits hosts that do not have a universal production flag. This
provides a safe development default while clearly requiring a shared secret for multi-instance
production and continuity across restarts.

`LiveRegionPage` binds a typed page, region, safe loader, invalidation flow and shared authorization
check. Every host has `handlers.liveRegion` at one generated page route. Reserved query parameters
select document, SSE or safe refresh; no additional application input classes are necessary. HTML
`liveRegion` attributes tell the shipped client to connect and reload the declared region. SSE still
carries IDs only and uses existing admission, coalescing, heartbeat and reconnect behavior. The check
runs on all three request modes; its default is explicitly anonymous, not inferred from page code.

Low-level protocol and identity APIs remain available to advanced adapters. No reflection, retained
server-side page session, RPC transport, Node build requirement for the application or string-built
HTML is introduced. The live binding initially addresses one page-level region; multi-region or
per-session subscriptions remain available through the lower-level API.

## Alternatives considered

- Alias protocol types into the host package: hides imports but leaves application synchronization.
- Keep hidden ordering fields in every command: mixes domain and presentation state.
- Push HTML over SSE: loses the existing safe refresh and bounded latest-only invalidation design.
- Guess a production Spring profile or Ktor environment name: unreliable; warn clearly and validate
  configured secrets instead.

## Consequences

Enhanced actions, deferred regions and live updates need no protocol-package imports. Native forms
and ordinary GET navigation retain their HTTP behavior. Applications still write epoch metadata in
the document head, supply stable keys for repeated regions and authorize every concrete resource.
Rotating the secret invalidates old region IDs; deployments must coordinate keys. Plain pages need
no JavaScript. Live pages include only the shipped module and keep normal navigation as fallback.

The shipped runtime remains below its fixed 36,000 raw / 12,500 gzip / 11,000 Brotli byte ceilings.
The reviewed #215 measurement change is 34,567 to 35,958 minified bytes; no ceiling is raised.

## Follow-up

Keep gradient fixtures, the checked baseline and generated agent guidance aligned. Retain tests for
fresh and resumed IDs, ordering validation, shared authorization and adapter parity. Preserve the
existing low-level SSE admission and browser ordering tests. Signed expiring context tokens and
manifest/key-rotation compatibility remain separate work; this change does not claim to implement
the planned token integrity model in ADR 0010.

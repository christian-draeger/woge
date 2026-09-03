# ADR 0008: Make native and enhanced paths share secure boundaries

- Status: Accepted
- Date: 2026-09-03
- Decision owners: Woge maintainers
- Related issues: [#5](https://github.com/christian-draeger/woge/issues/5), [#6](https://github.com/christian-draeger/woge/issues/6), [#7](https://github.com/christian-draeger/woge/issues/7), [#8](https://github.com/christian-draeger/woge/issues/8), [#10](https://github.com/christian-draeger/woge/issues/10), [#34](https://github.com/christian-draeger/woge/issues/34), [#41](https://github.com/christian-draeger/woge/issues/41), [#42](https://github.com/christian-draeger/woge/issues/42), [#43](https://github.com/christian-draeger/woge/issues/43)

## Context

Woge renders application data into complete HTML and streamed patches, dispatches generated actions, and mutates an existing DOM. The same convenience that removes route and selector strings could create dangerous implicit trust if registration were confused with authorization, server origin with safe content, or a DOM target ID with a capability.

Native forms and enhanced fetch requests must not have different security behavior. Framework integrations such as Spring Security are valuable, but an annotation or authenticated principal cannot decide application-specific permission by itself.

## Decision

The immutable boundary and defaults are:

1. Browser requests, persisted content and client-reported identity/revision data are untrusted.
2. Host adapters perform transport parsing, size limits, authentication integration and CSRF verification, then pass only Woge-owned values and verified facts through the host SPI.
3. Portable application/domain code authorizes every page, action and live subscription for the concrete resource before response commit. Generated registration proves only that an entry point exists.
4. HTML output is context-encoded by default. Raw HTML crosses an explicit auditable unsafe/trusted type boundary; ordinary strings never acquire raw semantics implicitly.
5. Patch operations use opaque generated rendered-instance/region references scoped to an active page epoch, not CSS selectors. Protocol version, target, epoch and revision are validated before mutation.
6. Ordinary patch application does not execute scripts, inline event handlers or active URL schemes. Woge's runtime requires no `eval`, inline executable script or hydration graph.
7. Native and enhanced unsafe requests share CSRF, authorization, validation and replay controls. A non-idempotent enhanced request is not automatically retried without a stable idempotency key.
8. Redirects use validated Woge URL values and remain same-origin/application-local by default. External redirects require an explicit application allowlist policy.
9. Streaming parsers, queues and work are bounded. Arbitrary network chunks are never message boundaries; disconnect and timeout cancel structured work.
10. Production diagnostics expose safe categories and correlation IDs while redacting credentials, cookies, CSRF material, raw submitted values, rendered HTML and stack traces.

The attack inventory, trust map, threat IDs, detailed controls and review cadence live in the versioned [threat model](../security/threat-model.md). Changing these defaults or moving a trust boundary requires a superseding ADR. Adding evidence, threats or stricter controls can update the living model directly.

## Alternatives considered

- **Trust all server-produced patches:** rejected because stored data, unsafe application code or a compromised extension can still introduce active content.
- **Sanitize every HTML string with one generic filter:** rejected because output contexts differ and a sanitizer cannot replace context-aware encoding; explicit raw HTML remains an auditable exception.
- **Use arbitrary CSS selectors as patch targets:** rejected because selector injection, over-broad matches and client/server ownership become hard to reason about.
- **Treat action registration as authorization:** rejected because generated descriptors cannot express every domain/resource policy.
- **Delegate all CSRF behavior to each host without a Woge contract:** rejected because native and enhanced paths or server adapters could drift silently.
- **Allow arbitrary redirect strings:** rejected because user-controlled return parameters become open redirects and active URL sinks.
- **Rely only on CSP:** rejected because CSP is defense in depth and cannot repair authorization, unsafe output or protocol confusion.
- **Send detailed development errors to every client:** rejected because streaming and remote clients make environment mistakes easy and sensitive values can leak after commit.

## Consequences

### Positive

- One security contract applies across Spring MVC, Spring WebFlux, Ktor and no-JavaScript flows.
- Kotlin types remove common raw-HTML, URL, target and operation confusion before runtime.
- Browser patching stays compatible with strict CSP and ordinary external modules.
- Threat IDs connect architectural assumptions to implementation and negative tests.

### Negative

- Adapters need explicit CSRF/security translation and parity tests.
- Raw HTML and external redirects require visible ceremony.
- Patch parsing and application must validate metadata and active content in addition to framing.
- Applications still need domain authorization and deployment hardening; Woge cannot make those implicit.

## Follow-up

- Apply output and URL context controls in [#16](https://github.com/christian-draeger/woge/issues/16) and [#42](https://github.com/christian-draeger/woge/issues/42).
- Enforce patch target, active-content and parser controls in [#19](https://github.com/christian-draeger/woge/issues/19), [#21](https://github.com/christian-draeger/woge/issues/21) and [#41](https://github.com/christian-draeger/woge/issues/41).
- Test CSRF/idempotency parity in [#34](https://github.com/christian-draeger/woge/issues/34) and authorization lifecycle parity through [#65](https://github.com/christian-draeger/woge/issues/65).
- Verify strict CSP and Trusted Types-compatible sinks in [#43](https://github.com/christian-draeger/woge/issues/43).
- Require each security-relevant implementation PR to cite and exercise the applicable `WOGE-*` threat IDs.

# ADR 0106: Stream typed deferred documents with native DPU

- Status: Accepted
- Date: 2026-10-11
- Decision owners: Woge maintainers
- Related issues: [#230](https://github.com/christian-draeger/woge/issues/230)
- Refines: [0015](0015-limit-native-dpu-to-initial-document-optimization.md),
  [0027](0027-fetch-deferred-patches-after-html-shell.md)
- Partially supersedes: [0086](0086-experimental-native-dpu-encoder.md)

## Context

Native Declarative Partial Updates only address the document owned by the original navigation
parser. A detached fetch cannot reach it. The existing HTML frame DSL closes every element within a
frame; rewriting shell strings or letting applications write unmatched raw tags would weaken its
escaping and streaming guarantees. Loading and resolved content can have different eligibility.

## Decision

`deferredHtmlPage` declares a transport-neutral head, visible shell, bounded set of body-level
deferred regions, and tail. Typed protocol frames open and close the document. Each application block
uses the ordinary HTML DSL; no whole-document buffering, shell regex rewriting, unsafe transport
escape hatch or dependency on experimental spikes is introduced.

All three page adapters prepare this document through the shared `InitialDocumentRuntime`, then
use their existing document response path. Admission precedes commitment. Head, shell, region loading
states and tail flush before deferred work starts. `DeferredRegionExecutor` retains scoped
concurrency, cancellation, controlled failures and timeouts. The original response remains
`text/html`, `Cache-Control: no-store`, `Vary: Cookie`; other Vary fields and security metadata survive.
Conditional validators are removed from these non-cacheable responses. HEAD never collects work.

Spring binds `woge.native-dpu.mode`; Ktor exposes `nativeDpuMode` and the matching configuration key.
Modes are `off` (default), `auto`, `force-native`, `force-fallback`. Only this typed document route is
affected. Automatic selection requires exactly one `woge_dpu=1` signal; absent, unsupported or
ambiguous signals fall back. The optional `dpu.js` asset refreshes a host-only, SameSite=Lax capability
cookie with a one-day lifetime, Secure on HTTPS, using the reflected browser capability. No
User-Agent or cookie content enters observations. Forced modes are experimental test overrides,
never eligibility overrides.

Each eligible loading fragment gets an opaque native parser range around its region wrapper.
Its eligible completed replacement uses a generated `<template for>` containing the same region ID
and next revision. The original parser performs the replacement, even with the module disabled.
Each range has one terminal replacement; append, actions and live updates remain framed.

Eligibility is checked independently before each region's loading boundary and before its completed
native template. Unsafe provenance and active or parser-sensitive content fail closed. A late
rejection does not roll back already flushed siblings, refetch work, switch the response media type,
or put rejected HTML in a native template.

Instead, rejected updates use the existing length-prefixed PatchStreamV1 encoder, base64-wrapped in
inert HTML templates on the same response. A trailing hidden readiness marker ensures the optional
module never reads a partly parsed envelope. The module shares the production decoder and validated
DOM sink; native templates never pass through its fragment parser. It snapshots the current region
registry for each framed update because the native parser may have replaced sibling wrappers.
The framed stream retains its preamble and terminal count. Native and framed updates share response
patch-count and byte limits; existing document limits additionally count the HTML envelope.

This new opt-in route is distinct from ADR 0027's supported post-shell fetch route. That endpoint
continues sending framed bytes, never native templates. Applications still supply authorized
complete-HTML links; active content rejected by the fallback DOM sink leaves useful loading and
navigation in place and emits a safe error event.

## Alternatives considered

- **Rewrite shell markup at runtime:** loses structural ownership and creates parser ambiguity.
- **Wait for every region before selecting native:** defeats early shell delivery.
- **Refetch on late rejection:** repeats work and moves cancellation outside the original request.
- **Expose raw processing-instruction writers:** makes eligibility an application convention.
- **Use inline executable patch scripts:** incompatible with strict CSP and unnecessary.
- **Use native DPU as the default:** does not meet the supported cross-browser or beta contract.

## Consequences

The page Kotlin is unchanged between modes and hosts. Chromium conformance navigates real MVC,
WebFlux and Ktor adapters and compares text, accessibility and revisions, with the module disabled
for the forced-native case. Dedicated browser tasks are not dependencies of root `check` or the
ordinary browser configuration. JVM conformance checks negotiation, late fallback, HEAD and gated
shell-first delivery. The optional module has separate package and byte-budget entries; the core
JavaScript ceiling is unchanged. Plain server applications receive classpath assets without Node.

Initial document regions are deliberately independent body-level blocks. Nested table/select
layouts, browser-owned interactive state and repeated updates should use the stable framed route.
Native content does not offer the fallback runtime's browser-state preservation/lifecycle events.
The optimization remains experimental, Chrome-only and not a beta requirement.

## Follow-up

- Wire the dedicated three-adapter browser command into the existing Chromium browser CI job.
- Revalidate the reflected capability signal against browser changes; add a behavioral canary if
  reflection no longer proves support. Short-lived cookies are negotiation hints, not authentication.
- Expand parser contexts or append only with native/fallback behavior and security evidence.
- Keep the stable fetch, action and live channels framed until independently specified.

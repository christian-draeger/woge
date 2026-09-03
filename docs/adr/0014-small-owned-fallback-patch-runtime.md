# ADR 0014: Own a small protocol-specific fallback patch runtime

- Status: Accepted
- Date: 2026-09-03
- Decision owners: Woge maintainers
- Related issues: [#3](https://github.com/christian-draeger/woge/issues/3), [#4](https://github.com/christian-draeger/woge/issues/4), [#21](https://github.com/christian-draeger/woge/issues/21), [#25](https://github.com/christian-draeger/woge/issues/25), [#41](https://github.com/christian-draeger/woge/issues/41), [#42](https://github.com/christian-draeger/woge/issues/42)

## Context

Woge must preserve enhanced actions and deferred patches when native Declarative Partial Updates (DPU) are unavailable. The fallback has to consume Woge's length-prefixed protocol, survive arbitrary Fetch stream chunks, enforce active-page identity/revision rules and keep ordinary server HTML as the no-JavaScript baseline.

The [cross-browser spike](../../spikes/fallback-patch-runtime/evidence.md) implemented that narrow path in 8,050 source bytes (2,706 gzip; 2,284 Brotli). Eighteen tests passed in current Playwright Chromium, Firefox and WebKit builds. They include one-byte delivery, representative splits, executable-markup rejection, stale/unknown targets and malformed framing.

Existing choices solve adjacent problems. Google's `template-for-polyfill` 0.1.0 follows emerging `<template for>` syntax but buffers instead of streaming and does not understand Woge frames, terminal states or identity metadata. htmx 2.0.10 offers a broader request, selector, swap, event and history model whose wire semantics are not Woge's typed protocol.

## Decision

Woge owns one small browser fallback runtime for its versioned patch protocol. It is a protocol adapter and DOM sink, not a component framework, virtual DOM, client state graph or general navigation library.

The fallback:

- reads bytes incrementally from a standard Fetch `ReadableStream` and treats only protocol lengths as frame boundaries;
- resolves opaque generated region IDs from the active page registry rather than accepting selectors;
- verifies protocol version, page epoch and contiguous target revision before mutation;
- parses each complete HTML frame through an inert template and rejects ordinary executable patch content by default;
- distinguishes complete, safe remote error and transport truncation;
- supports only operations whose ordering, preservation and failure semantics are explicitly versioned and tested.

The initial operation is atomic child replacement. Valid earlier frames may remain visible if a later frame fails. The runtime is compatible with strict CSP and requires neither `unsafe-inline` nor `unsafe-eval`. Server-side context encoding and explicit trusted/raw HTML remain the primary XSS boundary; client rejection is defense in depth rather than a general sanitizer.

Native DPU is implemented behind the same internal patch-application capability once [#3](https://github.com/christian-draeger/woge/issues/3) proves its exact behavior. Runtime selection uses feature detection and preserves the same observable identity, ordering, completion and security contracts. Native syntax is not emitted merely because an API name exists.

`template-for-polyfill`, htmx, Turbo or another frontend library may receive optional adapters when a real consumer demonstrates value. None is a transitive dependency of Woge core or changes the canonical wire protocol. Packaging and loading of the production runtime are decided with the component-distribution work; this ADR fixes ownership and semantics, not the artifact layout.

The M0 JavaScript is executable evidence only. Production code must use bounded buffering/backpressure, generated/golden protocol fixtures, cancellation, stable diagnostics and the full security corpus.

## Alternatives considered

- **Depend only on `template-for-polyfill`:** rejected because it targets parser-level DPU markup, buffers, and lacks Woge framing, identity, limits and terminal semantics. It remains relevant to the native-path spike.
- **Use htmx as the mandatory runtime:** rejected because its broad selector/request/swap contract would become a second Woge API and still require protocol-specific framing and revision code.
- **Use Turbo or another navigation/component runtime:** rejected for the same contract overlap and because navigation/hydration is not required to apply a Woge patch.
- **Ship native DPU only:** rejected because Woge's supported browsers cannot depend on an experimental feature and the HTML baseline alone would lose enhanced updates.
- **Send one JSON response and replace after download:** rejected because Base64/escaping and whole-response buffering discard incremental patch delivery and explicit truncation semantics.
- **Let host adapters choose unrelated client protocols:** rejected because Spring MVC, WebFlux and Ktor applications would no longer share browser behavior or portable tests.

## Consequences

### Positive

- Spring and Ktor adapters share one small, testable browser protocol contract.
- Applications keep standards HTML, CSS, custom elements and optional islands without framework hydration.
- Native DPU can replace an internal sink later without forcing a public API rewrite.
- Runtime size and security surface stay narrow and measurable.
- Optional htmx/native-syntax integrations remain possible at explicit adapter boundaries.

### Negative

- Woge owns security updates, browser compatibility, byte parsing and DOM lifecycle tests.
- The first implementation cannot inherit the richer history, animation, morphing and extension ecosystems of general libraries.
- Native and fallback paths require parity tests as DPU evolves.
- Frame-level buffering means a single large HTML fragment is not progressively parsed.

## Follow-up

- Use [#3](https://github.com/christian-draeger/woge/issues/3) to decide the native DPU mapping and capability detection without weakening fallback guarantees.
- Implement the production decoder and runtime with golden JVM/browser fixtures in [#20](https://github.com/christian-draeger/woge/issues/20) and [#21](https://github.com/christian-draeger/woge/issues/21).
- Add append/preserve, focus and stale-response behavior in [#25](https://github.com/christian-draeger/woge/issues/25), [#35](https://github.com/christian-draeger/woge/issues/35) and [#37](https://github.com/christian-draeger/woge/issues/37).
- Move the negative fixtures into the full malformed/XSS/CSP suites in [#41](https://github.com/christian-draeger/woge/issues/41) and [#42](https://github.com/christian-draeger/woge/issues/42).
- Keep production runtime packaging aligned with the distribution decision in [#76](https://github.com/christian-draeger/woge/issues/76).

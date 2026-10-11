# ADR 0086: Keep native DPU an explicit initial-document encoder

- Status: Accepted
- Date: 2026-10-11
- Decision owners: Woge maintainers
- Related issues: [#40](https://github.com/christian-draeger/woge/issues/40)

## Context

[ADR 0015](0015-limit-native-dpu-to-initial-document-optimization.md) limits Chrome's Declarative Partial Updates (DPU) to an opt-in initial-document optimization. Woge's current deferred-region adapter fetches framed patches after the HTML shell ([ADR 0027](0027-fetch-deferred-patches-after-html-shell.md)); a response parsed as a detached fetch fragment cannot reach the already parsed document. Sending native templates through that endpoint would look enabled while silently leaving loading content in place.

## Decision

The server runtime now contains an experimental encoder from the existing `ReplacePatch` IR to range processing instructions and `<template for>`. It does not change page, component, action, or region DSLs. The existing post-shell deferred endpoint remains on framed fallback transport; native output is only valid while the original HTML document parser owns the response.

The selection policy is internal to `woge-server-runtime`: `off` (default), `auto`, `force-native` and `force-fallback`. There is no Spring property, Ktor option or browser-side cookie writer yet, because no host route can use native output. A public switch that changes nothing would mislead applications. `auto` already defines the future capability rule: exactly one `woge_dpu=1` cookie plus eligible markup; a missing, duplicated or other value selects fallback. No User-Agent inference is used. A future native document route must send `Cache-Control: no-store` and vary on `Cookie`. `force-native` and `force-fallback` exist for tests; forced native still refuses ineligible markup.

Names are generated UUID-based opaque values. A range start emits `<?start name="…">fallback<?end>`; the semantic Replace emits `<template for="…">…</template>`. Eligibility is checked before encoding: raw/unsafe HTML provenance, executable elements, inline event handlers, `srcdoc`, executable URL schemes, inline style and multi-URL attributes are ineligible. The caller must use the supported framed fallback before committing that region.

Replace closes/replaces one addressed range. A range is the placeholder boundary and can carry useful loading HTML. Append is not a native append operation: every non-terminal emitted template must re-emit the marker so the next template can address the target. This issue wires Replace only; range construction is exposed internally as the initial boundary primitive and append remains unimplemented.

## Alternatives considered

- **Send native templates from the existing deferred fetch endpoint:** rejected because fragment parsing cannot patch the live document.
- **Select by User-Agent:** rejected because it does not prove runtime support and is cache-hostile.
- **Make DPU the default:** rejected because other engines lack the feature and an absent capability signal must fall back.
- **Allow raw HTML when it appears harmless:** rejected because the native parser activates markup; provenance is retained and raw HTML is ineligible.

## Consequences

### Positive

- One transport-neutral Replace IR can drive native and framed serialization without changing application Kotlin.
- Missing/stale capability safely selects fallback, and markup is checked before native serialization.
- Native behavior is tested in Chromium against fallback semantic outcomes, not template/processing-instruction residue.

### Negative

- `auto` does not change the current post-shell fetched deferred response; an original-document streaming route is required before a host can select native output in production.
- Capability cookie variation requires private/no-store response caching for any future native document route.
- Append and dynamic/action updates remain fallback-only.

## Follow-up

- [#230](https://github.com/christian-draeger/woge/issues/230): add native selection to a host-owned original-document streaming route only when it can emit shell boundaries and deferred patches on the same parser response; do not redirect the framed post-shell endpoint.
- Revisit range and append mappings with browser evidence and cross-engine support changes.
- Expose the Spring property, the Ktor option and the client capability cookie together with that route, not before.
- Keep forced modes test-only and retain the framed path as the default until a complete host route exists.

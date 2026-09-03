# ADR 0015: Limit native DPU to an opt-in initial-document optimization

- Status: Accepted
- Date: 2026-09-03
- Decision owners: Woge maintainers
- Related issues: [#3](https://github.com/christian-draeger/woge/issues/3), [#4](https://github.com/christian-draeger/woge/issues/4), [#11](https://github.com/christian-draeger/woge/issues/11), [#19](https://github.com/christian-draeger/woge/issues/19), [#21](https://github.com/christian-draeger/woge/issues/21), [#42](https://github.com/christian-draeger/woge/issues/42)

## Context

Chrome 150 shipped `<template for>` out-of-order streaming and HTML processing instructions. The public WICG design continues to evolve and other Woge-supported engines do not yet provide the same path. Woge needs to decide how much of this now-real browser primitive to expose without making Chrome syntax, parser trust or browser timing part of its Kotlin API.

The [native DPU spike](../../spikes/native-dpu/evidence.md) ran real streamed HTTP fixtures in Chrome for Testing 151. Marker replacement, range replacement, repeated append, nested/parent/shadow scoping, a body-to-head patch and completion-order delivery worked in stable configuration. Script-initiated `streamAppendHTMLUnsafe` required the experimental flag. A fragment parser could not reach the existing document.

The security fixture is decisive: a script and inline error handler delivered inside a native parser patch both executed. Native DPU supplies parser patching, not Woge's inert-patch, epoch, revision, framing, limit or terminal-error contract.

## Decision

The maximum MVP support for native DPU is an opt-in limited-availability encoder for initial-document completion-order patches on Chrome 150 and newer. It remains an internal implementation of transport-neutral Patch IR and does not appear in component, action, page, Spring, Ktor or application-domain APIs.

The adapter is off by default until its capability negotiation and parity suite ship. An application enabling it accepts the named Chrome-only optimization tier; every affected region still provides the complete or linked HTML-only fallback required by [ADR 0007](0007-browser-support-and-progressive-enhancement.md). Unsupported, absent or unadvertised capability uses the complete-page or Woge fallback path. User-agent sniffing is not capability detection.

Initial mappings are deliberately narrow:

- child/range replacement uses generated `<?start name="…">` and `<?end>` instructions;
- ordered append uses a generated marker that each non-terminal template deliberately re-emits;
- generated templates are placed in the narrowest owning parent/tree scope;
- a body-level template is reserved for a page-owned target that genuinely needs page/head reach;
- target names are opaque, page-scoped generated values and never application selectors.

The native channel accepts only Woge-structured, context-encoded markup whose element/attribute stream is known to contain no active content. Raw/unsafe HTML, script, inline handlers, `srcdoc`, executable URLs and equivalent escape hatches make the native encoder ineligible and fail or route through a separately reviewed path before bytes are committed. Native parser behavior is never described as sanitization.

The stable action, enhanced-navigation and live-update path continues to use the framed cross-browser runtime from [ADR 0014](0014-small-owned-fallback-patch-runtime.md). Woge does not use experimental `streamAppendHTMLUnsafe` for those MVP responses: it would still need the Woge decoder and identity checks, adds active parser semantics, and cannot express terminal/truncation or revision rules itself.

Client-side feature detection may use the reflected `HTMLTemplateElement.prototype.htmlFor` capability, backed by a behavioral canary if browser evidence requires it. Because the server cannot observe a browser property on the first request, native response selection requires an explicit previously established client capability signal or application/deployment opt-in strategy. A missing or stale signal must fail toward the supported fallback, not toward an unresolved loading-only page.

An unresolved native template remains in the DOM and becomes a development diagnostic signal. It is not a reliable production error transport. Native/fallback parity tests own content, order and accessibility outcomes; exact DOM marker/template residue is adapter-internal.

This ADR refines the possible native adapter anticipated by ADRs 0007 and 0014. It does not make the Chrome-only path part of Woge's supported cross-browser enhancement tier.

## Alternatives considered

- **Use native DPU for every Chrome patch:** rejected because action/live patches still need framing, identity, revisions, safe terminal errors and active-content controls.
- **Make Chrome DPU the default initial response:** rejected because first-request capability is not directly available and unresolved templates/loading content would weaken the HTML baseline elsewhere.
- **Infer support from the User-Agent header:** rejected because it is brittle, proxy/cache-hostile and cannot verify runtime behavior.
- **Adopt `template-for-polyfill` for every non-Chrome browser:** rejected as a mandatory layer because it buffers, has parser-observation differences and would add another runtime path; it remains test/reference input.
- **Ignore native DPU until all browsers ship:** rejected because an isolated encoder and executable fixture let Woge learn from the platform without exposing it publicly.
- **Allow trusted/raw HTML because it came from the server:** rejected because the observed native sink executes active markup and server origin does not prove every rendered value or extension is safe.

## Consequences

### Positive

- Woge can exploit real browser completion-order parsing without coupling Kotlin applications to proposal syntax.
- Spring MVC, WebFlux and Ktor retain identical public ports and Patch IR.
- Cross-browser security, ordering and failure semantics remain canonical.
- Generated parent/tree placement uses the platform's scope boundary instead of page-wide names by default.
- The opt-in tier can expand if interoperability and safe insertion APIs improve.

### Negative

- The MVP may ship no enabled native adapter until negotiation/parity work is complete.
- Native initial documents and framed updates need separate encoder/TCK coverage.
- Raw HTML or executable extensions cannot use the native optimization.
- Capability signaling across requests adds cache-variation and expiry concerns.
- Chrome's native DOM residue/error behavior is less explicit than Woge protocol errors.

## Follow-up

- Keep the fixture pinned and update the exact Chrome status in release evidence; any non-Chromium implementation triggers parity testing before support expands.
- Model generated range/marker encoding behind Patch IR in [#19](https://github.com/christian-draeger/woge/issues/19), not in component APIs.
- Add active-content eligibility and hostile-markup parity to [#42](https://github.com/christian-draeger/woge/issues/42).
- Design capability signaling, cache variation and fallback behavior with enhanced navigation rather than adding browser checks to server adapters.
- Revisit experimental Streaming HTML methods only after they ship in the stable supported matrix and provide a measurable benefit over Woge's small runtime.

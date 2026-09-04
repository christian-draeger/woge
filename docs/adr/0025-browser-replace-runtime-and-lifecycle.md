# ADR 0025: Apply Replace patches through a page-local DOM registry

- Status: Accepted
- Date: 2026-09-04
- Decision owners: Woge maintainers
- Related issues: [#21](https://github.com/christian-draeger/woge/issues/21), [#31](https://github.com/christian-draeger/woge/issues/31), [#36](https://github.com/christian-draeger/woge/issues/36), [#37](https://github.com/christian-draeger/woge/issues/37), [#42](https://github.com/christian-draeger/woge/issues/42), [#80](https://github.com/christian-draeger/woge/issues/80)

## Context

[ADR 0014](0014-small-owned-fallback-patch-runtime.md) selects a small Woge-owned browser adapter,
and [ADR 0024](0024-strict-bounded-patch-stream-codec.md) fixes the JVM framing and validation
contract. The production browser runtime must now consume exactly those bytes and mutate only the
intended active-page region. It must preserve ordinary HTML, CSS and custom-element capabilities
without introducing a virtual DOM, hydration graph or application-wide client state model.

DOM replacement also crosses lifecycle boundaries. Application controllers may own listeners or
other resources in removed descendants, custom elements have native connection callbacks and open
dialogs/popovers have browser-managed state. Woge needs a small predictable hook without defining the
future headless-component API or broadening this issue into Fetch action enhancement.

JavaScript numbers cannot exactly represent every non-negative Kotlin `Long`. Parsing revision values
through ordinary JSON numbers would silently corrupt valid version-1 metadata above `2^53 - 1`.

## Decision

Ship `client/woge-fallback-client` as a dependency-free production ES module built with esbuild. It is
an independent web artifact, not a JVM module. Its public entry point creates one
`WogePatchRuntime` for one active `Document` and accepts a standard
`ReadableStream<Uint8Array>`. Request creation, link/form interception and history remain outside this
module and are added by [#31](https://github.com/christian-draeger/woge/issues/31).

The browser `PatchStreamDecoder` mirrors version-1 framing: it uses declared byte lengths rather than
Fetch chunks, retains at most one bounded frame, requires exact content types and strict UTF-8,
recognizes one terminal frame and rejects trailing or truncated data. The decoder consumes the JVM
Golden fixture at every two-chunk split and one byte at a time.

Version-1 metadata has one canonical representation. The browser validates its exact field order,
spelling, primitive form and constrained string alphabets before constructing an event. It parses
interaction and revision counters as `BigInt`, bounds them to Kotlin's signed `Long` range and checks
the contiguous revision step. This avoids a second lossy JavaScript-number protocol.

One `PageRegionRegistry` is constructed from:

- exactly one `meta[name="woge-page-epoch"]` in the active document head;
- opaque `data-woge-region` values;
- canonical `data-woge-revision` counters;
- optional `data-woge-interaction-sequence`, defaulting to the initial sequence `0`.

The static selector used to discover Woge-owned region attributes is implementation code. Patch
metadata is looked up only as a `Map` key and is never interpolated into `querySelector`, implementing
`WOGE-TARGET-001`. Duplicate, malformed, unknown or externally changed registrations fail before DOM
mutation. A replacement preflights nested region registrations and commits their registry change only
with the successful child replacement.

The runtime checks active epoch, exact interaction sequence and contiguous target revision before
parsing HTML into a detached `template`. It then rejects the same active elements, inline handlers,
`srcdoc`, multi-URL attributes and unsupported URL schemes as the server encoder. Validation rejects;
it never cleans or rewrites. Negative browser fixtures require the old DOM and revision to remain
unchanged, implementing `WOGE-XSS-002` as defense in depth.

After all protocol, identity, HTML and nested-registry checks pass, replacement follows this order:

1. dispatch bubbling, composed `woge:before-replace` on the stable target;
2. close open dialog/popover descendants that will be removed;
3. call the standard `replaceChildren` API with the inert fragment;
4. advance the target revision and page-local nested-region registry;
5. dispatch bubbling, composed `woge:after-replace` on the same target.

The region element itself is retained. Woge does not rewrite its classes, styles, attributes or
custom-element instance. Application code can register one delegated listener on the document to
dispose old child controllers before replacement, mount new controllers afterwards and update a
controller attached to the stable region. Descendant custom elements independently receive native
`disconnectedCallback` and `connectedCallback`. This is a lifecycle seam, not a controller framework.

A server Error frame becomes `WogeRemotePatchError` with only its constrained code, correlation ID
and recovery intent. Local framing/DOM failures use stable `WogePatchError` codes without echoing
metadata or HTML. Abort signals cancel and release the stream reader; downstream cancellation does not
become a protocol dependency.

CI builds the minified module, runs Node decoder tests and the same DOM contract in Chromium, Firefox
and WebKit, reports minified/gzip/Brotli size and attaches module-load/parse/evaluation plus patch-apply
timings. Numeric release budgets remain owned by [#46](https://github.com/christian-draeger/woge/issues/46).

## Alternatives considered

- **Use patch targets as CSS selectors:** rejected because selectors can over-match, couple identity
  to styling and let protocol input choose arbitrary DOM nodes.
- **Use JavaScript Number for counters:** rejected because valid Kotlin `Long` metadata can silently
  round to another revision or interaction sequence.
- **Re-scan the DOM and select a target for every patch:** rejected because a mutable selector result
  is weaker than one active-page registry with explicit nested-region changes.
- **Replace the region element itself:** rejected because stable application classes, custom-element
  ownership and delegated listeners on that element would be discarded unnecessarily.
- **Silently sanitize hostile HTML:** rejected because hidden output changes obscure defects and make
  JVM/browser policy parity harder to verify.
- **Define a controller registry in the patch runtime:** rejected because headless behavior and its
  accessibility contracts belong to #80. Standard delegated lifecycle events are sufficient here.
- **Intercept links and forms now:** rejected because request policy, fallback semantics, CSRF and
  history need the separate vertical work in #31.

## Consequences

### Positive

- Kotlin and browser runtimes share one executable Golden wire representation.
- Opaque protocol input cannot become an arbitrary selector.
- Invalid metadata or active content cannot partially mutate one Replace operation.
- Application-owned classes, CSS, custom elements and standard overlay APIs remain usable.
- Headless behavior has a small mount/update/dispose seam without global hydration.
- Bundle cost and browser timings remain visible on every relevant change.

### Negative

- Woge owns a small binary decoder and active-content policy in both Kotlin and JavaScript.
- Strict canonical metadata intentionally rejects equivalent JSON from unrelated encoders.
- One complete HTML patch is buffered and parsed before atomic application.
- Lifecycle events cannot preserve focus, selection or dirty form values by themselves.
- Trusted Types integration and a broader hostile-markup corpus are still follow-up work.

## Follow-up

- Connect enhanced forms and Fetch cancellation to this runtime in
  [#31](https://github.com/christian-draeger/woge/issues/31).
- Add explicit focus, selection and dirty-control preservation in
  [#36](https://github.com/christian-draeger/woge/issues/36).
- Complete stale-interaction recovery behavior in
  [#37](https://github.com/christian-draeger/woge/issues/37).
- Add Trusted Types, strict CSP and the full hostile-markup matrix in
  [#42](https://github.com/christian-draeger/woge/issues/42).
- Build accessible opt-in controllers over the lifecycle seam in
  [#80](https://github.com/christian-draeger/woge/issues/80).

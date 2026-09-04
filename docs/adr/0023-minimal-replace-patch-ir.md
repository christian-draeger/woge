# ADR 0023: Keep Replace Patch IR semantic, explicit and closed

- Status: Accepted
- Date: 2026-09-04
- Decision owners: Woge maintainers
- Related issues: [#19](https://github.com/christian-draeger/woge/issues/19), [#20](https://github.com/christian-draeger/woge/issues/20), [#21](https://github.com/christian-draeger/woge/issues/21)

## Context

Woge needs one representation of a visible update before it can encode fallback streams, map to
possible native browser syntax or apply updates to the DOM. That representation must preserve the
identity and ordering rules from [ADR 0010](0010-identity-epochs-and-revisions.md) without exposing a
CSS selector or one transport's frame format.

Only atomic child replacement has executable evidence. Adding append, remove or announcement names
without their ordering, focus, accessibility and recovery behavior would let callers request
operations that no adapter can implement consistently.

HTML content crosses two different safety boundaries. Contextual server-side escaping prevents
ordinary strings from becoming markup. Separately, the browser patch sink must reject executable
elements, inline handlers and active URL schemes. Calling all escaped HTML “trusted” would hide this
second check.

## Decision

The version-1 semantic Patch IR is a sealed `Patch` interface with exactly one implementation:
`ReplacePatch`. Its operation is explicitly `PatchOperation.REPLACE`. External modules cannot invent
new implementations, and exhaustive consumers must handle every operation when Woge adds one.

Every replace patch carries:

- `PatchProtocolVersion`, currently version 1;
- an opaque `PatchId`;
- a `PatchTarget` combining the active `PageEpoch` and generated `RegionTargetId`;
- the browser's `InteractionSequence`;
- an exact contiguous `TargetRevisionStep` from base to base plus one;
- one materialized `PatchHtml` payload.

Opaque IDs permit only ASCII letters, digits, underscore and hyphen. This is safe in generated HTML
attributes and deliberately excludes selector syntax such as `#id`, `.class`, brackets, spaces and
path expressions. Parsing an ID proves syntax only. Epoch and target possession never grants
authorization, implementing `WOGE-TARGET-001`.

Interaction sequences and target revisions are non-negative 64-bit values. A revision step rejects
duplicates, gaps and overflow during construction. Overflow requires a new page epoch rather than
wrapping. Browser policy still decides whether an older interaction is ignored or a mismatch triggers
resynchronization; the IR makes all required facts available without deciding against browser state.

`patchHtml { ... }` renders through Woge's context-escaping HTML DSL and materializes exactly one
patch payload. This does not buffer a complete page and matches the selected length-prefixed framing,
which must know one payload's byte length before emitting its header. Direct construction from a raw
string is not public Kotlin API. An explicit unsafe HTML opt-in inside the DSL remains possible for
audited application code.

`PatchHtml` is context-encoded, not automatically trusted or inert. The version-1 encoder and browser
sink must still reject scripts, inline event handlers, `srcdoc` and dangerous active URL schemes
before bytes are emitted or the DOM changes, implementing `WOGE-XSS-002`.

The IR itself has no JSON, binary framing, DOM or native DPU API. Its fields are immutable primitive
value objects in deterministic order. A canonical test-only JSON fixture proves that every field and
the rendered payload can be serialized repeatably. The production metadata JSON mapping and golden
wire bytes belong to [#20](https://github.com/christian-draeger/woge/issues/20), where changing field
names becomes an explicit protocol decision.

## Alternatives considered

- **Use CSS selectors as targets:** rejected because they can over-match, couple updates to styling
  and bypass generated page-local target ownership.
- **Represent operations as a string:** rejected because an unknown spelling could travel until a
  browser silently ignores it. A sealed model and enum force explicit support.
- **Add append/remove/announce variants now:** rejected because their identity, ordering,
  preservation and accessibility rules do not yet have vertical evidence.
- **Store only target and HTML:** rejected because old documents, out-of-order interactions,
  duplicates and revision gaps would be indistinguishable.
- **Store a lazy HTML lambda:** rejected for patches because the selected frame header needs a known
  payload length and the browser applies complete patch frames atomically.
- **Annotate the model with one serialization library now:** rejected because it would make a codec
  dependency part of the semantic API before the version-1 encoder owns canonical JSON behavior.
- **Treat escaped HTML as safe to insert without inspection:** rejected because deliberate elements
  and URL-bearing attributes can still be executable even when every dynamic string is correctly
  escaped.

## Consequences

### Positive

- Native and fallback transports can consume the same visible-update intent.
- Missing epoch, interaction or revision facts cannot create a valid replace patch.
- Selector injection and unsupported operation strings are absent from the public model.
- Golden fixtures are deterministic without coupling application code to the wire codec.
- HTML context encoding and active-content validation remain visible, separately testable controls.

### Negative

- One patch payload is materialized in memory and must be bounded by the encoder's 8 MiB limit.
- Callers cannot request append, removal or announcements until those operations have complete
  semantics.
- Generated target IDs and cryptographic page epochs are not created by this module; later runtime
  work must supply them.
- The encoder performs a second active-content validation even for DSL-produced markup.

## Follow-up

- Encode canonical metadata and payload bytes with arbitrary-split golden tests in
  [#20](https://github.com/christian-draeger/woge/issues/20).
- Apply replace patches through the page-local registry and inert DOM parsing in
  [#21](https://github.com/christian-draeger/woge/issues/21).
- Generate opaque keyed region identities in [#25](https://github.com/christian-draeger/woge/issues/25).
- Add append only with its source sequence, event identity, bounded gap and recovery behavior.

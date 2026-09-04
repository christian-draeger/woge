# ADR 0024: Validate bounded canonical patch frames before exposure

- Status: Accepted
- Date: 2026-09-04
- Decision owners: Woge maintainers
- Related issues: [#20](https://github.com/christian-draeger/woge/issues/20), [#21](https://github.com/christian-draeger/woge/issues/21), [#41](https://github.com/christian-draeger/woge/issues/41)

## Context

[ADR 0013](0013-length-prefixed-patch-framing.md) fixes the version-1 wire layout and limits. The
production JVM codec must now translate the semantic Replace IR into that layout and survive any
network chunking. A decoder is a security boundary: lengths are attacker-controlled, JSON can contain
duplicate or ambiguous fields, UTF-8 can be malformed and otherwise well-formed HTML can deliberately
contain executable browser content.

The server encoder is also part of the boundary. Application code can reach Woge's explicit unsafe
HTML escape hatch, so “the server produced it” is not enough reason to put a script into an enhanced
DOM patch. At the same time, the policy must preserve useful web platform features such as custom
elements, classes, data/ARIA attributes, CSS custom properties and safe links.

## Decision

Implement a streaming `PatchStreamEncoder` over a synchronous `ByteSink` and an incremental
`PatchStreamDecoder`. Neither API owns the HTTP response, closes a stream or assumes that one sink
write equals one network read.

The encoder emits the fixed `WOGE` plus binary-version preamble once, then writes patch frames and
exactly one complete or error terminal frame. It validates one entire patch before emitting any byte
of that patch. Downstream write failures, including cancellation signals, propagate unchanged and
make that encoder unusable.

The decoder retains at most one version-1 frame. It validates unsigned declared lengths before growing
the buffer: metadata is limited to 64 KiB, HTML payload to 8 MiB and content type to 255 bytes. It can
consume one-byte reads, every possible two-chunk split or several frames in one read. An event becomes
visible only after its complete frame, content type, UTF-8, metadata, payload and operation-specific
rules are valid.

Version-1 metadata is strict canonical UTF-8 JSON. Woge uses the stable `kotlinx.serialization` JSON
tree parser, requires the exact field set and primitive types, constructs the typed IR, re-encodes it
in canonical field order and requires byte-for-byte text equality. This rejects unknown and duplicate
keys, alternate ordering, extra whitespace, quoted numbers and non-canonical numeric forms. The
semantic IR remains free of serialization annotations and codec types.

Patch metadata is decoded and validated before HTML text is decoded or inspected. Only then can a
`PatchStreamEvent.PatchFrame` be returned. Completion metadata must equal the number of preceding
patches. Terminal frames have no payload, and missing terminal data, truncation or bytes after a
terminal frame fail explicitly.

Before encoding or returning a patch event, Woge parses its HTML fragment with the current stable
jsoup HTML parser and rejects active content. The initial deny rules cover script/style/embedded
document and metadata elements, inline `on*` handlers, `srcdoc`, multi-URL attributes and URL-bearing
attributes that are neither a validated application URL nor an absolute URL with a supported
non-script scheme.
The 8 MiB byte limit is checked before HTML parsing. The browser sink repeats equivalent validation
before DOM mutation because server validation is defense in depth, not a client trust signal. This
implements `WOGE-XSS-002`.

The active-content check validates and rejects; it never cleans, rewrites or silently removes markup.
It is deliberately not a general HTML sanitizer. Ordinary custom elements, classes, data/ARIA
attributes, inline standards CSS without active embedded resources and safe links pass unchanged.

Local malformed input raises `PatchStreamException` with a stable `PatchStreamErrorCode` and a
payload-independent message. A decoded terminal application error is data instead:
`RemotePatchFailure` contains only a constrained Woge code, correlation ID and recovery intent. No
raw metadata, HTML, request value or stack trace enters those diagnostics, implementing
`WOGE-DIAG-001` and `WOGE-FRAME-001`.

A checked-in hexadecimal fixture locks the complete preamble, Patch frame, canonical metadata, HTML
payload and Complete frame. Codec tests cover every two-chunk split, one-byte reads, malformed lengths,
UTF-8, metadata, active content, truncation and terminal sequencing.

## Alternatives considered

- **Copy the spike's growing byte-array decoder unchanged:** rejected because one large transport
  read could retain an unbounded number of frames before validation.
- **Treat each host write or Fetch chunk as a frame:** rejected because HTTP, compression, TLS and
  proxies freely split and combine those chunks.
- **Write a new general JSON parser:** rejected because a maintained stable JSON implementation is a
  smaller security and correctness risk. Canonical re-encoding supplies the protocol strictness.
- **Put serialization annotations on Patch IR:** rejected because wire field names and one JSON
  library would leak into the transport-neutral application model.
- **Trust all DSL-produced HTML:** rejected because explicit unsafe markup and deliberate active
  elements still exist beyond contextual escaping.
- **Run a sanitizer that removes unsafe nodes:** rejected because silent output changes hide defects
  and make server/browser parity difficult to reason about.
- **Block all custom elements or inline styles:** rejected because Woge intentionally preserves
  modern HTML and CSS authoring. The policy targets executable/active content, not framework-owned
  markup conventions.
- **Return partly decoded frames:** rejected because consumers could apply HTML before discovering
  invalid metadata or a truncated payload.

## Consequences

### Positive

- Framework adapters can stream deterministic bytes without sharing their HTTP APIs.
- Arbitrary transport splits and compression do not affect application frame boundaries.
- Resource, metadata and active-content failures are typed and fail before event exposure.
- One Golden fixture can be consumed by JVM and browser implementations.
- Web-native classes, custom elements and modern CSS remain available in patches.

### Negative

- The JVM protocol runtime adds internal dependencies on kotlinx.serialization JSON and jsoup.
- Strict canonical metadata rejects semantically equivalent JSON emitted by unrelated encoders.
- Each atomic patch payload is materialized and parsed once before output.
- The browser runtime must implement and test an equivalent active-content policy in JavaScript.

## Follow-up

- Consume the Golden fixture and mirror validation in the browser Replace runtime in
  [#21](https://github.com/christian-draeger/woge/issues/21).
- Add malformed length, metadata, target and active-content fuzzing in
  [#41](https://github.com/christian-draeger/woge/issues/41).
- Measure real server flush, compression and proxy behavior with the streamed page slice in
  [#23](https://github.com/christian-draeger/woge/issues/23).
- Add new operation metadata only with an explicit IR variant and exhaustive encoder/decoder tests.

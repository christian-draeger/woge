# ADR 0013: Use explicit length-prefixed patch frames

- Status: Accepted
- Date: 2026-09-03
- Decision owners: Woge maintainers
- Related issues: [#4](https://github.com/christian-draeger/woge/issues/4), [#6](https://github.com/christian-draeger/woge/issues/6), [#19](https://github.com/christian-draeger/woge/issues/19), [#20](https://github.com/christian-draeger/woge/issues/20), [#21](https://github.com/christian-draeger/woge/issues/21), [#23](https://github.com/christian-draeger/woge/issues/23), [#41](https://github.com/christian-draeger/woge/issues/41), [#45](https://github.com/christian-draeger/woge/issues/45)

## Context

Fetch `ReadableStream` chunks are arbitrary transport observations, not application message boundaries. HTML can contain every normal text delimiter, UTF-8 code points span reads, and compression/proxies may split or combine output differently from the server's writes. Woge therefore needs explicit incremental framing for fallback patches, completion and safe post-commit errors.

The [framing spike](../../spikes/patch-framing/evidence.md) implemented length-prefix and `multipart/mixed` encoders/decoders. Both passed every possible two-chunk split, one-byte reads and gzip transport. For a 240-byte metadata/payload fixture, length-prefix used 114 overhead bytes versus multipart's 465 and required less parser/encoder code.

## Decision

The MVP fallback stream uses `application/vnd.woge.patch-stream; version=1` with a five-byte `WOGE`/binary-version preamble. Each frame has a fixed ten-byte header: kind, content-type length, unsigned big-endian metadata length and unsigned big-endian payload length, followed by those three exact byte regions.

Frame kinds are:

1. **Patch:** canonical UTF-8 JSON metadata plus arbitrary payload bytes; initial payload content type is `text/html; charset=utf-8`.
2. **Complete:** terminal, empty payload and canonical JSON completion metadata.
3. **Error:** terminal, empty payload and safe `application/problem+json` metadata containing only allowed code, correlation ID and recovery intent.

Metadata is limited to 64 KiB and payload to 8 MiB before allocation in version 1. Content type is limited to 255 UTF-8 bytes and rejects control characters. Unknown preamble versions, frame kinds, invalid/oversized lengths, malformed metadata, target/revision violations, truncation, missing terminal frames and bytes after terminal fail closed.

The browser parser operates on bytes after normal HTTP content decoding and retains partial header/content state across arbitrary reads. It validates metadata and active page/target/revision rules before parsing/applying HTML. A frame becomes observable only when all declared bytes are present and valid.

The encoder writes the HTML sink into the current frame payload accounting boundary; it does not assume one sink write equals one network or parser read. Production implementations use bounded cursor/ring buffers and backpressure rather than the spike's copy-oriented arrays.

Errors before response commit use normal HTTP status/content negotiation. After commit, only a safe terminal error frame is possible; transport loss remains distinguishable as truncation. Completion and error are mutually exclusive.

## Alternatives considered

- **`multipart/mixed`:** rejected for the MVP because browsers expose the response as bytes rather than incremental MIME parts, so Woge still needs a parser while paying boundary/header/Base64 overhead and more states.
- **ReadableStream chunk equals frame:** rejected because HTTP stacks, TLS, compression and proxies can split/combine writes arbitrarily.
- **Newline/record-separator/NDJSON framing:** rejected for arbitrary HTML because escaping or Base64 expands payloads and incremental UTF-8/text handling remains necessary.
- **One complete JSON document:** rejected because it buffers the whole stream and delays shell/patch application.
- **SSE for action/deferred responses:** rejected as the universal framing because EventSource is GET-oriented and text-only; SSE remains appropriate for live one-way updates.
- **No explicit completion frame:** rejected because clean completion, truncation and safe application error would be ambiguous after HTTP status is committed.
- **64-bit unbounded lengths:** rejected because browser-safe numeric handling and resource limits are simpler with reviewed version-1 ceilings well below 32-bit limits.

## Consequences

### Positive

- Arbitrary HTML and Unicode need no delimiter escaping or Base64.
- Parsers survive any byte split and validate size before allocation.
- Completion, application error and transport truncation are distinguishable.
- Per-frame content type leaves room for non-HTML payloads in a later protocol version without guessing.
- The format has substantially lower measured overhead than the multipart prototype.

### Negative

- Network traces need a small decoder rather than being directly readable text.
- Woge owns a versioned binary parser in JVM and browser runtimes.
- Proxy buffering can still delay valid frames and requires operational testing.
- Changing ceilings/header fields requires protocol-version compatibility work.

## Follow-up

- Put target, epoch, base/next revision and operation metadata into canonical Patch IR JSON in [#19](https://github.com/christian-draeger/woge/issues/19).
- Implement golden version-1 encoder/decoder fixtures across JVM and browser in [#20](https://github.com/christian-draeger/woge/issues/20) and [#21](https://github.com/christian-draeger/woge/issues/21).
- Add random splits, lengths, metadata and malformed target fuzzing in [#41](https://github.com/christian-draeger/woge/issues/41).
- Measure actual flush/compression/proxy behavior separately in [#23](https://github.com/christian-draeger/woge/issues/23) and [#45](https://github.com/christian-draeger/woge/issues/45).
- Generate a human-readable diagnostic decoder with the production protocol implementation.

# Patch framing evidence

Recorded on 2026-09-03 with Kotlin 2.4.0 and Gradle 8.14.4.

## Fixture and verification

The stream contains two HTML patches and one completion frame. Its payload includes UTF-8 (`🐺`), CR/LF text and a boundary-like string. Metadata contains target and base/next revision fields. The error fixture replaces completion with a safe problem code and correlation ID.

For both codecs, tests feed:

- every split `0..encodedSize` as two reads;
- the entire stream as individual one-byte reads;
- the gzip-compressed/decompressed stream as individual bytes;
- a truncated stream;
- unknown frame kind/version or oversized length data.

All valid permutations decode to byte-identical content type, metadata and payload. Invalid streams fail without emitting the malformed frame, and `finish()` rejects a missing terminal/final boundary.

## Measured result

```text
payload_and_metadata_bytes=240
length_prefixed_bytes=354
multipart_bytes=705
length_prefixed_overhead_bytes=114
multipart_overhead_bytes=465
```

The prototype source (encoder plus incremental decoder) is 101 lines for length-prefix and 146 lines for multipart, excluding shared frame types and tests. These are comparison values, not production size claims.

| Concern | Length-prefixed | `multipart/mixed` |
| --- | --- | --- |
| Arbitrary payload bytes | Exact byte length | Exact `Content-Length` inside each MIME part |
| Chunk splitting | Fixed state/remaining-byte counts | Boundary, header, body and final-boundary states |
| Metadata | Length-delimited UTF-8 | Base64url header value to avoid header injection |
| Sample overhead | 114 bytes | 465 bytes |
| Human inspection | Needs decoder/hexdump | Headers are readable but metadata is encoded |
| Boundary collision | None | Avoided by honoring part content length; boundary parsing still required between parts |
| Browser implementation | `DataView`/byte queue | MIME parsing must be implemented because Fetch does not expose multipart body parts incrementally |

Multipart's standard media type did not provide a native incremental browser parser, so its extra headers/boundaries did not remove client code. A text delimiter or NDJSON alternative would need escaping/Base64 for arbitrary HTML and still require an incremental text decoder.

## Proposed version 1 format

Response media type:

```text
application/vnd.woge.patch-stream; version=1
```

Preamble:

| Bytes | Meaning |
| ---: | --- |
| 4 | ASCII `WOGE` |
| 1 | binary protocol version (`0x01`) |

Each frame:

| Bytes | Meaning |
| ---: | --- |
| 1 | kind: patch `1`, complete `2`, safe error `3` |
| 1 | UTF-8 content-type byte length |
| 4 | unsigned big-endian metadata byte length |
| 4 | unsigned big-endian payload byte length |
| variable | content type |
| variable | UTF-8 metadata (canonical JSON in production) |
| variable | payload bytes |

The spike bounds metadata at 64 KiB and payload at 8 MiB before allocation. These are initial safety ceilings, configurable only downward until production evidence justifies a reviewed change. Content type is explicit per frame. Complete/error are terminal and have an empty payload; bytes after a terminal frame fail.

Patch metadata is parsed and semantically validated before HTML reaches the DOM sink. A post-commit error frame contains only a stable safe code, correlation ID and allowed recovery intent. Stack traces, user input and rendered HTML never enter it. A pre-commit failure remains a normal HTTP error response.

## Limitations before production

- Prototype decoders append/copy byte arrays for clarity; production uses a bounded cursor/ring buffer.
- Tests prove framing survives content decoding, not that proxies flush promptly. Buffering measurements remain in [#45](https://github.com/christian-draeger/woge/issues/45).
- Canonical JSON schema, protocol negotiation and browser TypeScript/JavaScript implementation land with Patch IR and fallback encoder/runtime.
- The 8 MiB ceiling is a safety maximum, not a recommendation to emit large patches.

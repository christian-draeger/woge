# Encode and decode fallback patch streams

The fallback patch stream is Woge's compact byte protocol for enhanced actions and deferred regions.
Application code normally creates `ReplacePatch` values; Spring MVC, Spring WebFlux or Ktor adapter
code turns them into response bytes.

The implementation exists now. The browser-side DOM runtime that consumes these events lands in the
next issue.

## Encode incrementally

Set the HTTP response content type to `PatchStreamV1.MEDIA_TYPE`, then connect the adapter's byte write
to a `ByteSink`:

```kotlin
val encoder = PatchStreamV1.encoder(ByteSink { bytes -> response.write(bytes) })

encoder.write(firstPatch)
encoder.write(secondPatch)
encoder.complete()
```

The encoder writes in order but does not flush or close `response`. That lifecycle belongs to the host
adapter. If rendering later fails after some patches are visible, the adapter can finish with safe
terminal metadata:

```kotlin
encoder.error(
    RemotePatchFailure(
        code = RemoteFailureCode.of("WOGE_RENDER_FAILED"),
        correlationId = RemoteCorrelationId.of("trace-42"),
        recovery = RecoveryIntent.RELOAD,
    ),
)
```

Complete and Error are mutually exclusive. Writing after either terminal event fails. A downstream
write exception is propagated unchanged, so an adapter can connect it to request cancellation and
structured cleanup.

## Do not treat writes as frames

One `ByteSink.write` call is only an encoder implementation detail. A proxy, compression layer or TCP
connection can split or combine those bytes. The five-byte preamble and ten-byte per-frame header are
the only frame boundaries.

The header declares exact content-type, metadata and payload byte lengths. Version 1 rejects metadata
over 64 KiB, HTML over 8 MiB and content types over 255 bytes before allocating beyond one bounded
frame.

## Decode arbitrary chunks

Feed exactly the byte chunks supplied by the transport:

```kotlin
val decoder = PatchStreamV1.decoder()

while (true) {
    val bytes = source.read() ?: break
    decoder.feed(bytes).forEach(::handleValidatedEvent)
}
decoder.finish()
```

`feed` returns only complete `PatchStreamEvent` values:

- `PatchFrame` contains a fully validated semantic `Patch`;
- `Complete` confirms the preceding patch count;
- `Error` contains constrained public failure metadata and a recovery intent.

Call `finish` when the HTTP body ends. It distinguishes successful completion from a truncated frame
or a stream with no terminal event.

## Handle typed failures

Malformed local input throws `PatchStreamException`. Inspect its stable `code`, for example
`INVALID_PREAMBLE`, `METADATA_TOO_LARGE`, `INVALID_METADATA`, `ACTIVE_CONTENT`, `TRUNCATED_STREAM` or
`BYTES_AFTER_TERMINAL`. The message never includes the rejected HTML, JSON or request data.

Do not continue with the same decoder after a failure. It remembers and rethrows the terminal parsing
error. Do not turn a protocol error into best-effort DOM mutation; request a safe region refresh or
full reload according to the calling capability.

## Validation before visibility

For a Patch frame, Woge checks in this order:

1. declared lengths and exact content type;
2. strict UTF-8 and canonical JSON metadata;
3. protocol version, operation, opaque target and contiguous revision types;
4. strict UTF-8 HTML payload;
5. active-content policy.

Only then does the decoder return the event. Unknown/duplicate JSON fields and even harmless extra
whitespace are rejected because the versioned metadata has one canonical representation.

The active-content policy rejects scripts, inline handlers, embedded documents and unsafe URL schemes.
It is not a sanitizer and removes nothing silently. Custom elements, Tailwind-style classes,
data/ARIA attributes, CSS custom properties and validated application or HTTP(S) links remain normal
HTML.

The browser runtime performs equivalent checks again before mutation. Server validation protects
output generation; it does not make network bytes trusted.

## Golden compatibility fixture

[`patch-stream-v1.hex`](../../modules/woge-protocol/src/test/resources/fixtures/patch-stream-v1.hex)
is the exact encoded form of one Replace patch followed by Complete. JVM tests decode it at every
possible split and one byte at a time. The browser runtime will consume the same fixture so a field,
length, endianness or canonical-JSON change cannot drift silently.

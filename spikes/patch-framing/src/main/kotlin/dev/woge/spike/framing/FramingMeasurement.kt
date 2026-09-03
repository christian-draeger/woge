package dev.woge.spike.framing

public fun main() {
    val frames = measurementFrames()
    val lengthPrefixed = LengthPrefixedCodec.encode(frames)
    val multipart = MultipartCodec("woge-measurement-boundary").encode(frames)
    val payloadBytes = frames.sumOf { it.payload.size + it.metadata.encodeToByteArray().size }

    println("payload_and_metadata_bytes=$payloadBytes")
    println("length_prefixed_bytes=${lengthPrefixed.size}")
    println("multipart_bytes=${multipart.size}")
    println("length_prefixed_overhead_bytes=${lengthPrefixed.size - payloadBytes}")
    println("multipart_overhead_bytes=${multipart.size - payloadBytes}")
}

internal fun measurementFrames(): List<WireFrame> = listOf(
    WireFrame.patch(
        metadata = "{\"target\":\"summary\",\"baseRevision\":0,\"nextRevision\":1}",
        html = "<section><h2>Summary</h2><p>3 open · 🐺</p></section>",
    ),
    WireFrame.patch(
        metadata = "{\"target\":\"tasks\",\"baseRevision\":0,\"nextRevision\":1}",
        html = "<section><h2>Tasks</h2><p>Boundary-like: \\r\\n--woge</p></section>",
    ),
    WireFrame.complete("{\"patches\":2}"),
)

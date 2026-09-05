package dev.woge.spring.webflux

import dev.woge.host.PageResult
import dev.woge.host.ResponseCookie
import dev.woge.host.ResponseMetadata
import dev.woge.host.SameSite
import dev.woge.html.DEFAULT_HTML_CHUNK_CHARS
import dev.woge.html.HtmlSink
import dev.woge.html.StreamingHtmlSink
import dev.woge.protocol.HtmlFrame
import dev.woge.protocol.PatchStreamV1
import dev.woge.runtime.EncodedPatchChunk
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asPublisher
import kotlinx.coroutines.reactor.awaitSingle
import org.reactivestreams.Publisher
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.web.reactive.function.BodyInserter
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Flux
import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.Duration as JavaDuration
import org.springframework.http.ResponseCookie as SpringResponseCookie

internal suspend fun PageResult.toWebFluxResponse(): ServerResponse =
    when (this) {
        is PageResult.Document ->
            responseBuilder(metadata)
                .body(documentBody(this))
                .awaitSingle()

        is PageResult.Redirect ->
            responseBuilder(metadata)
                .location(URI.create(location.value))
                .build()
                .awaitSingle()

        is PageResult.Failure ->
            responseBuilder(metadata)
                .build()
                .awaitSingle()
    }

internal suspend fun Flow<EncodedPatchChunk>.toWebFluxPatchResponse(): ServerResponse =
    ServerResponse
        .ok()
        .contentType(MediaType.parseMediaType(PatchStreamV1.MEDIA_TYPE))
        .header("Cache-Control", "no-store")
        .body(patchBody(this))
        .awaitSingle()

private fun responseBuilder(metadata: ResponseMetadata): ServerResponse.BodyBuilder {
    val builder = ServerResponse.status(HttpStatusCode.valueOf(metadata.status.code))
    metadata.contentType?.let { builder.contentType(MediaType.parseMediaType(it.value)) }
    metadata.headers.forEach { header -> builder.header(header.name.value, header.value.value) }
    metadata.cookies.forEach { cookie -> builder.cookie(cookie.toSpringCookie()) }
    return builder
}

private fun ResponseCookie.toSpringCookie(): SpringResponseCookie {
    val builder =
        SpringResponseCookie
            .from(name.value, value.value)
            .path(path.value)
            .secure(secure)
            .httpOnly(httpOnly)
            .sameSite(sameSite.httpValue)
    maxAgeSeconds?.let { builder.maxAge(JavaDuration.ofSeconds(it)) }
    return builder.build()
}

private val SameSite.httpValue: String
    get() = name.lowercase().replaceFirstChar(Char::uppercase)

private fun documentBody(document: PageResult.Document): BodyInserter<Unit, ServerHttpResponse> =
    flushingBody { response -> document.flushGroups(response) }

private fun patchBody(chunks: Flow<EncodedPatchChunk>): BodyInserter<Unit, ServerHttpResponse> =
    flushingBody { response ->
        chunks
            .map { chunk -> Flux.just(response.bufferFactory().wrap(chunk.bytes)) }
            .asPublisher()
    }

private fun flushingBody(
    groups: (ServerHttpResponse) -> Publisher<out Publisher<out DataBuffer>>,
): BodyInserter<Unit, ServerHttpResponse> = BodyInserter { response, _ -> response.writeAndFlushWith(groups(response)) }

private fun PageResult.Document.flushGroups(response: ServerHttpResponse): Publisher<out Publisher<out DataBuffer>> =
    frames
        .map { frame ->
            Flux
                .fromIterable(frame.renderChunks())
                .map { bytes -> response.bufferFactory().wrap(bytes) }
        }.asPublisher()

private suspend fun HtmlFrame.renderChunks(): List<ByteArray> {
    val context = currentCoroutineContext()
    val chunks = mutableListOf<ByteArray>()
    val sink =
        StreamingHtmlSink(
            downstream =
                HtmlSink { value ->
                    context.ensureActive()
                    chunks += value.toByteArray(StandardCharsets.UTF_8)
                },
            maxChunkChars = DEFAULT_HTML_CHUNK_CHARS,
        )
    context.ensureActive()
    writeTo(sink)
    context.ensureActive()
    sink.flush()
    return chunks
}

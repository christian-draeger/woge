package dev.woge.spring.webflux

import dev.woge.host.ACTION_NAVIGATION_HEADER
import dev.woge.host.ACTION_VALIDATION_HEADER
import dev.woge.host.PageResult
import dev.woge.host.ResponseCookie
import dev.woge.host.ResponseMetadata
import dev.woge.host.ResponseStatus
import dev.woge.host.SameSite
import dev.woge.host.WogeObservationContext
import dev.woge.host.WogeObserver
import dev.woge.host.WogeOperation
import dev.woge.host.acceptsActionPatches
import dev.woge.host.enhancedActionNavigation
import dev.woge.html.HtmlByteBudget
import dev.woge.protocol.PatchStreamV1
import dev.woge.runtime.EncodedPatchChunk
import dev.woge.runtime.encodeActionPatchStream
import dev.woge.runtime.observeCollection
import dev.woge.runtime.renderByteChunks
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
import java.time.Duration as JavaDuration
import org.springframework.http.ResponseCookie as SpringResponseCookie

internal suspend fun PageResult.toWebFluxResponse(
    observer: WogeObserver,
    observationContext: WogeObservationContext,
    actionAccept: String? = null,
): ServerResponse =
    when (this) {
        is PageResult.RegionUpdates ->
            if (acceptsActionPatches(actionAccept)) {
                responseBuilder(metadata)
                    .contentType(MediaType.parseMediaType(PatchStreamV1.MEDIA_TYPE))
                    .headers { it.set("Cache-Control", "no-store") }
                    .header("Vary", "Accept")
                    .headers { headers -> focusSummary?.let { headers.set(ACTION_VALIDATION_HEADER, it.value) } }
                    .body(patchBody(encodeActionPatchStream(observer, observationContext.requestTrace)))
                    .awaitSingle()
            } else {
                nativeResult.toWebFluxResponse(observer, observationContext, actionAccept)
            }
        is PageResult.Document ->
            responseBuilder(metadata)
                .apply { if (actionAccept != null) header("Vary", "Accept") }
                .body(documentBody(this, observer, observationContext))
                .awaitSingle()

        is PageResult.Redirect ->
            enhancedActionNavigation(actionAccept)?.let { navigation ->
                responseBuilder(metadata, ResponseStatus.OK, varyAccept = true)
                    .header(ACTION_NAVIGATION_HEADER, navigation.value)
                    .headers { it.set("Cache-Control", "no-store") }
                    .build()
                    .awaitSingle()
            } ?: responseBuilder(metadata, varyAccept = actionAccept != null)
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

private fun responseBuilder(
    metadata: ResponseMetadata,
    status: ResponseStatus = metadata.status,
    varyAccept: Boolean = false,
): ServerResponse.BodyBuilder {
    val builder = ServerResponse.status(HttpStatusCode.valueOf(status.code))
    metadata.contentType?.let { builder.contentType(MediaType.parseMediaType(it.value)) }
    metadata.headers.forEach { header -> builder.header(header.name.value, header.value.value) }
    if (varyAccept) builder.header("Vary", "Accept")
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

private fun documentBody(
    document: PageResult.Document,
    observer: WogeObserver,
    observationContext: WogeObservationContext,
): BodyInserter<Unit, ServerHttpResponse> =
    flushingBody { response -> document.flushGroups(response, observer, observationContext) }

private fun patchBody(chunks: Flow<EncodedPatchChunk>): BodyInserter<Unit, ServerHttpResponse> =
    flushingBody { response ->
        chunks
            .map { chunk -> Flux.just(response.bufferFactory().wrap(chunk.bytes)) }
            .asPublisher()
    }

private fun flushingBody(
    groups: (ServerHttpResponse) -> Publisher<out Publisher<out DataBuffer>>,
): BodyInserter<Unit, ServerHttpResponse> = BodyInserter { response, _ -> response.writeAndFlushWith(groups(response)) }

private fun PageResult.Document.flushGroups(
    response: ServerHttpResponse,
    observer: WogeObserver,
    observationContext: WogeObservationContext,
): Publisher<out Publisher<out DataBuffer>> {
    val budget = HtmlByteBudget(maxBytes)
    return frames
        .observeCollection(observer, WogeOperation.SHELL_RENDER, observationContext)
        .map { frame ->
            Flux
                .fromIterable(frame.renderByteChunks(budget, observer, observationContext))
                .map { bytes -> response.bufferFactory().wrap(bytes) }
        }.asPublisher()
}

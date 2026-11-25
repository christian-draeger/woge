package dev.woge.quarkus

import dev.woge.host.ACTION_NAVIGATION_HEADER
import dev.woge.host.ACTION_VALIDATION_HEADER
import dev.woge.host.PageResult
import dev.woge.host.RequestMethod
import dev.woge.host.ResponseMetadata
import dev.woge.host.ResponseStatus
import dev.woge.host.WogeObservationContext
import dev.woge.host.WogeObserver
import dev.woge.host.WogeOperation
import dev.woge.host.acceptsActionPatches
import dev.woge.host.enhancedActionNavigation
import dev.woge.html.HtmlByteBudget
import dev.woge.protocol.PatchStreamV1
import dev.woge.runtime.EncodedPatchChunk
import dev.woge.runtime.LiveEventStream
import dev.woge.runtime.encodeActionPatchStream
import dev.woge.runtime.observeCollection
import dev.woge.runtime.renderByteChunks
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.Cookie
import io.vertx.core.http.CookieSameSite
import io.vertx.core.http.HttpServerResponse
import io.vertx.kotlin.coroutines.awaitEvent
import io.vertx.kotlin.coroutines.coAwait
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

internal suspend fun HttpServerResponse.respondWoge(
    result: PageResult,
    method: RequestMethod,
    observer: WogeObserver,
    observation: WogeObservationContext,
    accept: String? = null,
) {
    if (result is PageResult.RegionUpdates && !acceptsActionPatches(accept)) {
        respondWoge(result.nativeResult, method, observer, observation, accept)
        return
    }

    applyMetadata(result.metadata)
    when (result) {
        is PageResult.RegionUpdates -> {
            headers().add("Vary", "Accept")
            result.focusSummary?.let { putHeader(ACTION_VALIDATION_HEADER, it.value) }
            respondPatches(result.encodeActionPatchStream(observer, observation.requestTrace))
        }
        is PageResult.Document -> {
            if (accept != null) headers().add("Vary", "Accept")
            if (method != RequestMethod.HEAD) {
                val bytes =
                    flow {
                        val budget = HtmlByteBudget(result.maxBytes)
                        result.frames
                            .observeCollection(observer, WogeOperation.SHELL_RENDER, observation)
                            .collect { frame ->
                                frame.renderByteChunks(budget, observer, observation).forEach { emit(it) }
                            }
                    }
                writeChunks(bytes)
            }
        }
        is PageResult.Redirect -> {
            if (accept != null) headers().add("Vary", "Accept")
            val navigation = result.enhancedActionNavigation(accept)
            if (navigation == null) {
                putHeader("Location", result.location.value)
            } else {
                statusCode = ResponseStatus.OK.code
                putHeader(ACTION_NAVIGATION_HEADER, navigation.value)
                putHeader("Cache-Control", "no-store")
            }
        }
        is PageResult.Failure, is PageResult.NotModified -> Unit
    }
    end().coAwait()
}

internal suspend fun HttpServerResponse.respondPatches(chunks: Flow<EncodedPatchChunk>) {
    putHeader("Content-Type", PatchStreamV1.MEDIA_TYPE)
    putHeader("Cache-Control", "no-store, no-transform")
    putHeader("X-Accel-Buffering", "no")
    writeChunks(chunks.map { it.bytes })
}

internal suspend fun HttpServerResponse.respondLive(events: Flow<ByteArray>) {
    LiveEventStream.HEADERS.forEach { (name, value) -> putHeader(name, value) }
    putHeader("Content-Type", "text/event-stream; charset=utf-8")
    writeChunks(events)
}

private suspend fun HttpServerResponse.writeChunks(chunks: Flow<ByteArray>) {
    isChunked = true
    chunks.flowOn(Dispatchers.Default).buffer(0).collect { chunk ->
        if (writeQueueFull()) {
            try {
                awaitEvent<Void> { drainHandler(it) }
            } finally {
                drainHandler(null)
            }
        }
        write(Buffer.buffer(chunk)).coAwait()
    }
}

private fun HttpServerResponse.applyMetadata(metadata: ResponseMetadata) {
    statusCode = metadata.status.code
    metadata.headers.forEach { headers().add(it.name.value, it.value.value) }
    metadata.contentType?.let { putHeader("Content-Type", it.value) }
    metadata.cookies.forEach { cookie ->
        addCookie(
            Cookie.cookie(cookie.name.value, cookie.value.value).apply {
                path = cookie.path.value
                isSecure = cookie.secure
                isHttpOnly = cookie.httpOnly
                sameSite = CookieSameSite.valueOf(cookie.sameSite.name)
                cookie.maxAgeSeconds?.let { maxAge = it }
            },
        )
    }
}

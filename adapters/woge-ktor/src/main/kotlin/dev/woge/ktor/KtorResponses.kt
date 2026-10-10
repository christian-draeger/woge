package dev.woge.ktor

import dev.woge.host.ACTION_NAVIGATION_HEADER
import dev.woge.host.ACTION_VALIDATION_HEADER
import dev.woge.host.PageResult
import dev.woge.host.ResponseCookie
import dev.woge.host.ResponseMetadata
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
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.log
import io.ktor.server.request.httpMethod
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect

internal suspend fun ApplicationCall.respondWogePage(
    result: PageResult,
    observer: WogeObserver,
    observationContext: WogeObservationContext,
    actionAccept: String? = null,
) {
    when (result) {
        is PageResult.RegionUpdates -> {
            if (acceptsActionPatches(actionAccept)) {
                applyMetadata(result.metadata)
                response.headers.append(HttpHeaders.Vary, "Accept")
                result.focusSummary?.let { response.headers.append(ACTION_VALIDATION_HEADER, it.value) }
                respondWogePatches(result.encodeActionPatchStream(observer, observationContext.requestTrace))
            } else {
                respondWogePage(result.nativeResult, observer, observationContext, actionAccept)
            }
        }
        is PageResult.Document -> {
            if (actionAccept != null) response.headers.append(HttpHeaders.Vary, "Accept")
            respondDocument(result, observer, observationContext)
        }
        is PageResult.Redirect -> {
            applyMetadata(result.metadata)
            if (actionAccept != null) response.headers.append(HttpHeaders.Vary, "Accept")
            val navigation = result.enhancedActionNavigation(actionAccept)
            if (navigation == null) {
                response.headers.append(HttpHeaders.Location, result.location.value)
            } else {
                response.status(HttpStatusCode.OK)
                response.headers.append(ACTION_NAVIGATION_HEADER, navigation.value)
                response.headers.append(HttpHeaders.CacheControl, "no-store")
            }
            respond(BodylessContent)
        }

        is PageResult.NotModified,
        is PageResult.Failure,
        -> {
            applyMetadata(result.metadata)
            respond(BodylessContent)
        }
    }
}

internal suspend fun ApplicationCall.respondWogePatches(chunks: Flow<EncodedPatchChunk>) {
    response.headers.append(HttpHeaders.CacheControl, "no-store")
    respondBytesWriter(contentType = ContentType.parse(PatchStreamV1.MEDIA_TYPE)) {
        chunks.writeAndFlushKtorChunks { bytes ->
            writeFully(bytes)
            flush()
        }
    }
}

internal suspend fun Flow<EncodedPatchChunk>.writeAndFlushKtorChunks(writeAndFlush: suspend (ByteArray) -> Unit) {
    collect { chunk -> writeAndFlush(chunk.bytes) }
}

internal suspend fun ApplicationCall.respondWogePreStreamFailure(failure: Throwable) {
    application.log.error("Woge request failed before response streaming", failure)
    response.status(HttpStatusCode.InternalServerError)
    response.headers.append(HttpHeaders.CacheControl, "no-store")
    respond(BodylessContent)
}

private suspend fun ApplicationCall.respondDocument(
    document: PageResult.Document,
    observer: WogeObserver,
    observationContext: WogeObservationContext,
) {
    applyMetadata(document.metadata)
    if (request.httpMethod == HttpMethod.Head) {
        response.headers.append(HttpHeaders.ContentType, requireNotNull(document.metadata.contentType).value)
        respond(BodylessContent)
        return
    }

    respondBytesWriter(
        contentType = ContentType.parse(requireNotNull(document.metadata.contentType).value),
        status = HttpStatusCode.fromValue(document.metadata.status.code),
    ) {
        val budget = HtmlByteBudget(document.maxBytes)
        document.frames.observeCollection(observer, WogeOperation.SHELL_RENDER, observationContext).collect { frame ->
            frame.renderByteChunks(budget, observer, observationContext).forEach { bytes -> writeFully(bytes) }
            flush()
        }
    }
}

private fun ApplicationCall.applyMetadata(metadata: ResponseMetadata) {
    response.status(HttpStatusCode.fromValue(metadata.status.code))
    metadata.headers.forEach { header -> response.headers.append(header.name.value, header.value.value) }
    metadata.cookies.forEach { cookie -> response.headers.append(HttpHeaders.SetCookie, cookie.render()) }
}

private fun ResponseCookie.render(): String =
    buildString {
        append(name.value)
        append('=')
        append(value.value)
        append("; Path=")
        append(path.value)
        maxAgeSeconds?.let { maxAge ->
            append("; Max-Age=")
            append(maxAge)
        }
        if (secure) append("; Secure")
        if (httpOnly) append("; HttpOnly")
        append("; SameSite=")
        append(sameSite.httpValue)
    }

private val SameSite.httpValue: String
    get() = name.lowercase().replaceFirstChar(Char::uppercase)

private object BodylessContent : OutgoingContent.NoContent()

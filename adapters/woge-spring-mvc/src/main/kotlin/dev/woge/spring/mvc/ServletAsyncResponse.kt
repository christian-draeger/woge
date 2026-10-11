package dev.woge.spring.mvc

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
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration as JavaDuration
import org.springframework.http.ResponseCookie as SpringResponseCookie

internal suspend fun PageResult.writeToServlet(
    handoff: ServletResponseHandoff,
    request: HttpServletRequest,
    observer: WogeObserver,
    observationContext: WogeObservationContext,
    actionAccept: String? = null,
) {
    when (this) {
        is PageResult.RegionUpdates -> {
            if (acceptsActionPatches(actionAccept)) {
                encodeActionPatchStream(observer, observationContext.requestTrace).writeToServlet(
                    handoff,
                    metadata.status,
                ) { response ->
                    response.applyMetadata(metadata)
                    response.addHeader("Vary", "Accept")
                    focusSummary?.let { response.addHeader(ACTION_VALIDATION_HEADER, it.value) }
                }
            } else {
                nativeResult.writeToServlet(handoff, request, observer, observationContext, actionAccept)
            }
        }
        is PageResult.Document -> {
            val head = { response: HttpServletResponse ->
                response.applyMetadata(metadata)
                if (actionAccept != null) response.addHeader("Vary", "Accept")
            }
            if (request.method.equals("HEAD", ignoreCase = true)) {
                handoff.finish(head)
            } else {
                val budget = HtmlByteBudget(maxBytes)
                frames
                    .observeCollection(observer, WogeOperation.SHELL_RENDER, observationContext)
                    .map { frame -> frame.renderByteChunks(budget, observer, observationContext) }
                    .writeBody(handoff, head)
            }
        }

        is PageResult.Redirect ->
            handoff.finish { response ->
                response.applyMetadata(metadata)
                if (actionAccept != null) response.addHeader("Vary", "Accept")
                val navigation = enhancedActionNavigation(actionAccept)
                if (navigation == null) {
                    response.setHeader("Location", location.value)
                } else {
                    response.status = HttpServletResponse.SC_OK
                    response.setHeader(ACTION_NAVIGATION_HEADER, navigation.value)
                    response.setHeader("Cache-Control", "no-store")
                }
            }

        is PageResult.NotModified,
        is PageResult.Failure,
        -> handoff.finish { it.applyMetadata(metadata) }
    }
}

internal suspend fun Flow<EncodedPatchChunk>.writeToServlet(
    handoff: ServletResponseHandoff,
    status: ResponseStatus = ResponseStatus.OK,
    head: (HttpServletResponse) -> Unit = {},
) {
    map { listOf(it.bytes) }.writeBody(handoff) { response ->
        head(response)
        response.status = status.code
        response.contentType = PatchStreamV1.MEDIA_TYPE
        response.setHeader("Cache-Control", "no-store, no-transform")
        response.setHeader("X-Accel-Buffering", "no")
    }
}

/** Commits [head] only once the first bytes exist, so a failure before them still becomes a clean 500. */
internal suspend fun Flow<List<ByteArray>>.writeBody(
    handoff: ServletResponseHandoff,
    head: (HttpServletResponse) -> Unit,
) {
    var streaming = false
    collect { chunks ->
        currentCoroutineContext().ensureActive()
        if (!streaming) {
            handoff.stream(head)
            streaming = true
        }
        handoff.write(chunks)
    }
    if (!streaming) handoff.finish(head)
}

private fun HttpServletResponse.applyMetadata(metadata: ResponseMetadata) {
    status = metadata.status.code
    metadata.contentType?.let { contentType ->
        this.contentType = contentType.mediaType.value
        contentType.charset?.let { characterEncoding = it.value }
    }
    metadata.headers.forEach { header -> addHeader(header.name.value, header.value.value) }
    metadata.cookies.forEach { cookie -> addHeader("Set-Cookie", cookie.toSpringCookie().toString()) }
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

internal fun HttpServletResponse.writeMethodNotAllowed(allowedMethods: Iterable<String>) {
    status = HttpServletResponse.SC_METHOD_NOT_ALLOWED
    setHeader("Allow", allowedMethods.joinToString(", "))
    setHeader("Cache-Control", "no-store")
}

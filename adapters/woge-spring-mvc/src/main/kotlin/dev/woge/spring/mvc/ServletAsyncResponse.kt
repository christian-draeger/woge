package dev.woge.spring.mvc

import dev.woge.host.PageResult
import dev.woge.host.ResponseCookie
import dev.woge.host.ResponseMetadata
import dev.woge.host.SameSite
import dev.woge.html.DEFAULT_HTML_CHUNK_CHARS
import dev.woge.html.HtmlSink
import dev.woge.html.StreamingHtmlSink
import dev.woge.protocol.PatchStreamV1
import dev.woge.runtime.EncodedPatchChunk
import jakarta.servlet.AsyncEvent
import jakarta.servlet.AsyncListener
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level
import java.util.logging.Logger
import java.time.Duration as JavaDuration
import org.springframework.http.ResponseCookie as SpringResponseCookie

@Suppress("TooGenericExceptionCaught")
internal fun HttpServletRequest.launchWogeResponse(
    response: HttpServletResponse,
    dispatcher: CoroutineDispatcher,
    timeoutMillis: Long,
    block: suspend () -> Unit,
) {
    check(isAsyncSupported) {
        "Woge Spring MVC streaming requires Servlet async support on the servlet and every filter"
    }
    val async = startAsync(this, response)
    async.timeout = timeoutMillis
    val terminal = AtomicBoolean()
    lateinit var job: Job
    job =
        CoroutineScope(dispatcher).launch(
            context = CoroutineName("woge-spring-mvc-response"),
            start = CoroutineStart.LAZY,
        ) {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                logger.log(Level.SEVERE, "Woge Spring MVC response failed", failure)
                response.writeSafeServerFailure()
            } finally {
                if (terminal.compareAndSet(false, true)) {
                    runCatching(async::complete)
                }
            }
        }
    async.addListener(ServletCoroutineListener(job, terminal, response))
    job.start()
}

private class ServletCoroutineListener(
    private val job: Job,
    private val terminal: AtomicBoolean,
    private val response: HttpServletResponse,
) : AsyncListener {
    override fun onComplete(event: AsyncEvent) {
        terminal.set(true)
        job.cancel(CancellationException("Servlet async response completed"))
    }

    override fun onTimeout(event: AsyncEvent) {
        response.writeSafeServerFailure()
        job.cancel(CancellationException("Servlet async response timed out"))
        if (terminal.compareAndSet(false, true)) {
            runCatching(event.asyncContext::complete)
        }
    }

    override fun onError(event: AsyncEvent) {
        terminal.set(true)
        job.cancel(CancellationException("Servlet async response failed", event.throwable))
    }

    override fun onStartAsync(event: AsyncEvent) {
        event.asyncContext.addListener(this)
    }
}

internal suspend fun PageResult.writeToServlet(
    request: HttpServletRequest,
    response: HttpServletResponse,
) {
    when (this) {
        is PageResult.Document -> {
            response.applyMetadata(metadata)
            if (!request.method.equals("HEAD", ignoreCase = true)) {
                writeDocument(response)
            }
        }

        is PageResult.Redirect -> {
            response.applyMetadata(metadata)
            response.setHeader("Location", location.value)
        }

        is PageResult.Failure -> response.applyMetadata(metadata)
    }
}

internal suspend fun Flow<EncodedPatchChunk>.writeToServlet(response: HttpServletResponse) {
    response.status = HttpServletResponse.SC_OK
    response.contentType = PatchStreamV1.MEDIA_TYPE
    response.setHeader("Cache-Control", "no-store")
    val output = response.outputStream
    collect { chunk ->
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        output.write(chunk.bytes)
        output.flush()
    }
}

private suspend fun PageResult.Document.writeDocument(response: HttpServletResponse) {
    val output = response.outputStream
    val context = kotlinx.coroutines.currentCoroutineContext()
    val chunks =
        StreamingHtmlSink(
            downstream =
                HtmlSink { value ->
                    context.ensureActive()
                    output.write(value.toByteArray(StandardCharsets.UTF_8))
                },
            maxChunkChars = DEFAULT_HTML_CHUNK_CHARS,
        )
    frames.collect { frame ->
        context.ensureActive()
        frame.writeTo(chunks)
        chunks.flush()
        output.flush()
    }
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

private fun HttpServletResponse.writeSafeServerFailure() {
    if (!isCommitted) {
        reset()
        status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
    }
}

internal fun HttpServletResponse.writeMethodNotAllowed(allowedMethods: Iterable<String>) {
    status = HttpServletResponse.SC_METHOD_NOT_ALLOWED
    setHeader("Allow", allowedMethods.joinToString(", "))
}

private val logger: Logger = Logger.getLogger("dev.woge.spring.mvc")

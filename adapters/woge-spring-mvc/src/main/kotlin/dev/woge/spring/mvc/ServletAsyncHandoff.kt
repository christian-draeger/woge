package dev.woge.spring.mvc

import jakarta.servlet.AsyncContext
import jakarta.servlet.AsyncEvent
import jakarta.servlet.AsyncListener
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.concurrent.withLock

/**
 * Releases the container thread and runs [block] on [dispatcher]. The block never touches the
 * response head itself: it hands status, headers and cookies to [ServletResponseHandoff], which
 * applies them on a container thread through `AsyncContext.dispatch()`. The Servlet spec runs that
 * dispatch only after the original dispatch has returned, so filters that add headers after
 * `chain.doFilter` (for example Spring Security) never race with Woge. See ADR 0091.
 */
@Suppress("TooGenericExceptionCaught")
internal fun HttpServletRequest.launchWogeResponse(
    response: HttpServletResponse,
    dispatcher: CoroutineDispatcher,
    timeoutMillis: Long,
    block: suspend ServletResponseHandoff.() -> Unit,
) {
    check(isAsyncSupported) {
        "Woge Spring MVC streaming requires Servlet async support on the servlet and every filter"
    }
    val async = startAsync(this, response)
    async.timeout = timeoutMillis
    val handoff = ServletResponseHandoff(async, System.currentTimeMillis() + timeoutMillis)
    setAttribute(HANDOFF_ATTRIBUTE, handoff)
    val job =
        CoroutineScope(dispatcher).launch(
            context = CoroutineName("woge-spring-mvc-response"),
            start = CoroutineStart.LAZY,
        ) {
            var failed = false
            try {
                handoff.block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                logger.log(Level.SEVERE, "Woge Spring MVC response failed", failure)
                failed = true
            } finally {
                handoff.close(failed)
            }
        }
    async.addListener(ServletCoroutineListener(job, handoff, response))
    job.start()
}

/**
 * Applies a head handed off by [launchWogeResponse]. Handlers call this first: it returns `true`
 * on the async re-dispatch, so decoding, security checks and the use case are not run again.
 */
internal fun HttpServletRequest.resumeWogeResponse(response: HttpServletResponse): Boolean {
    val handoff = getAttribute(HANDOFF_ATTRIBUTE) as? ServletResponseHandoff ?: return false
    handoff.resume(this, response)
    return true
}

/**
 * Worker-side handle that moves the response head onto a container thread and owns every body write.
 * Tomcat recycles the request and response right after async completion, so body writes and the
 * async listeners share [bodyLock]: once [end] ran, a late write can no longer reach the recycled
 * objects of the next request on that connection.
 */
internal class ServletResponseHandoff(
    @Volatile private var async: AsyncContext,
    private val deadlineMillis: Long,
) {
    val terminal = AtomicBoolean()
    private val bodyLock = ReentrantLock()
    private var headSent = false
    private var output: OutputStream? = null

    @Volatile
    private var pending: PendingHead? = null

    /** Ends a response without body: the container applies [head] and completes the request. */
    fun finish(head: (HttpServletResponse) -> Unit) {
        if (!headSent && terminal.compareAndSet(false, true)) {
            dispatch(PendingHead(head, null))
        }
    }

    /** Commits [head] on a container thread; afterwards this worker may call [write]. */
    suspend fun stream(head: (HttpServletResponse) -> Unit) {
        check(!headSent) { "Woge response head was already sent" }
        val resumed = CompletableDeferred<OutputStream>()
        dispatch(PendingHead(head, resumed))
        output = resumed.await()
    }

    /** Writes and flushes [chunks] unless the request already ended. */
    fun write(chunks: List<ByteArray>) {
        val target = checkNotNull(output) { "Woge response head was not sent" }
        bodyLock.withLock {
            if (terminal.get()) throw CancellationException("Servlet async response already ended")
            try {
                chunks.forEach(target::write)
                target.flush()
            } catch (failure: IOException) {
                throw clientDisconnected(failure)
            }
        }
    }

    /**
     * Called by the container before it recycles the response; waits for an in-flight [write].
     * Runs [action] and returns `true` only for the first end of this request.
     */
    fun end(action: () -> Unit = {}): Boolean =
        bodyLock.withLock {
            terminal.compareAndSet(false, true).also { first -> if (first) action() }
        }

    fun close(failed: Boolean) {
        if (!headSent) {
            finish { if (failed) it.writeSafeServerFailure() }
        } else if (terminal.compareAndSet(false, true)) {
            runCatching { async.complete() }
        }
    }

    fun resume(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val head = pending ?: return
        pending = null
        head.apply(response)
        val resumed = head.resumed ?: return
        async =
            request.startAsync(request, response).apply {
                timeout = (deadlineMillis - System.currentTimeMillis()).coerceAtLeast(1)
            }
        try {
            response.flushBuffer()
            resumed.complete(response.outputStream)
        } catch (failure: IOException) {
            resumed.completeExceptionally(clientDisconnected(failure))
        }
    }

    private fun dispatch(head: PendingHead) {
        headSent = true
        pending = head
        // Fails only when a timeout or disconnect already ended the request; the listener cancels this job.
        runCatching { async.dispatch() }
    }

    private class PendingHead(
        val apply: (HttpServletResponse) -> Unit,
        val resumed: CompletableDeferred<OutputStream>?,
    )
}

private class ServletCoroutineListener(
    private val job: Job,
    private val handoff: ServletResponseHandoff,
    private val response: HttpServletResponse,
) : AsyncListener {
    override fun onComplete(event: AsyncEvent) {
        job.cancel(CancellationException("Servlet async response completed"))
        handoff.end()
    }

    override fun onTimeout(event: AsyncEvent) {
        job.cancel(CancellationException("Servlet async response timed out"))
        if (handoff.end { response.writeSafeServerFailure() }) {
            runCatching(event.asyncContext::complete)
        }
    }

    override fun onError(event: AsyncEvent) {
        job.cancel(CancellationException("Servlet async response failed", event.throwable))
        handoff.end()
    }

    override fun onStartAsync(event: AsyncEvent) {
        event.asyncContext.addListener(this)
    }
}

/** A closed connection is a normal end of a web response, not a server failure. */
private fun clientDisconnected(failure: IOException) =
    CancellationException("Client disconnected").apply { initCause(failure) }

internal fun HttpServletResponse.writeSafeServerFailure() {
    if (!isCommitted) {
        reset()
        status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        setHeader("Cache-Control", "no-store")
    }
}

private val HANDOFF_ATTRIBUTE = ServletResponseHandoff::class.java.name

private val logger: Logger = Logger.getLogger("dev.woge.spring.mvc")

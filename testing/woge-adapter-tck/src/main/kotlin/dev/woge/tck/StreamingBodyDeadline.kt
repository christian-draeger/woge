package dev.woge.tck

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** The request deadline can finish at the headers when the response body is read as a stream. */
internal fun streamingBodyWithDeadline(timeout: Duration): HttpResponse.BodyHandler<InputStream> =
    HttpResponse.BodyHandler {
        HttpResponse.BodySubscribers.mapping(HttpResponse.BodySubscribers.ofInputStream()) { body ->
            DeadlineInputStream(body, timeout)
        }
    }

private val bodyDeadlines =
    ScheduledThreadPoolExecutor(1) { task ->
        Thread(task, "woge-tck-http-body-deadline").apply { isDaemon = true }
    }.apply {
        removeOnCancelPolicy = true
        setKeepAliveTime(1, TimeUnit.SECONDS)
        allowCoreThreadTimeOut(true)
    }

private enum class BodyState { OPEN, CLOSED, TIMED_OUT }

private class DeadlineInputStream(
    private val body: InputStream,
    private val timeout: Duration,
) : FilterInputStream(body) {
    private val state = AtomicReference(BodyState.OPEN)
    private val deadline = bodyDeadlines.schedule(::expire, timeout.toNanos(), TimeUnit.NANOSECONDS)

    override fun read(): Int = readWithinDeadline { super.read() }

    override fun read(
        bytes: ByteArray,
        offset: Int,
        length: Int,
    ): Int = readWithinDeadline { super.read(bytes, offset, length) }

    override fun close() {
        if (state.compareAndSet(BodyState.OPEN, BodyState.CLOSED)) {
            deadline.cancel(false)
            body.close()
        }
    }

    private fun expire() {
        if (state.compareAndSet(BodyState.OPEN, BodyState.TIMED_OUT)) {
            runCatching(body::close)
        }
    }

    private inline fun readWithinDeadline(read: () -> Int): Int {
        requireTimeRemaining()
        val count =
            try {
                read()
            } catch (failure: IOException) {
                requireTimeRemaining(failure)
                throw failure
            }
        requireTimeRemaining()
        if (count == -1) deadline.cancel(false)
        return count
    }

    private fun requireTimeRemaining(cause: IOException? = null) {
        if (state.get() == BodyState.TIMED_OUT) {
            // The budget contracts treat a transport IOException as an expected aborted response.
            throw IllegalStateException("TCK response body exceeded its $timeout read deadline", cause)
        }
    }
}

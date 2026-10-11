package dev.woge.spring.mvc

import jakarta.servlet.ServletOutputStream
import jakarta.servlet.WriteListener
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponseWrapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Regression for #246: Tomcat recycles a response after async completion, so late writes must stop. */
class ServletResponseHandoffTest {
    @Test
    fun `a write after the container ended the request never reaches the response`() {
        val body = ByteArrayOutputStream()
        val handoff = streamingHandoff(RecordingResponse(body))

        handoff.write(listOf("first".toByteArray()))
        assertTrue(handoff.end())
        assertThrows<CancellationException> { handoff.write(listOf("late".toByteArray())) }

        assertEquals("first", body.toString())
        assertFalse(handoff.end(), "only the first end runs its action")
    }

    @Test
    fun `ending the request waits for an in-flight write`() {
        val body = ByteArrayOutputStream()
        val response = RecordingResponse(body, blockWrites = true)
        val handoff = streamingHandoff(response)

        val writer = thread { handoff.write(listOf("in flight".toByteArray())) }
        assertTrue(response.writing.await(WAIT_SECONDS, TimeUnit.SECONDS))
        val ended = CountDownLatch(1)
        thread {
            handoff.end()
            ended.countDown()
        }
        assertFalse(ended.await(BLOCK_MILLIS, TimeUnit.MILLISECONDS), "end must wait for the write")

        response.release.countDown()
        writer.join()
        assertTrue(ended.await(WAIT_SECONDS, TimeUnit.SECONDS))
        assertEquals("in flight", body.toString())
    }

    private fun streamingHandoff(response: HttpServletResponse): ServletResponseHandoff {
        val request = MockHttpServletRequest().apply { isAsyncSupported = true }
        val async = request.startAsync(request, response)
        val handoff =
            ServletResponseHandoff(async, System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(WAIT_SECONDS))
        runBlocking {
            val streaming = launch(start = CoroutineStart.UNDISPATCHED) { handoff.stream { it.status = 200 } }
            handoff.resume(request, response)
            streaming.join()
        }
        return handoff
    }

    private class RecordingResponse(
        private val body: ByteArrayOutputStream,
        private val blockWrites: Boolean = false,
    ) : HttpServletResponseWrapper(MockHttpServletResponse()) {
        val writing = CountDownLatch(1)
        val release = CountDownLatch(1)

        override fun getOutputStream(): ServletOutputStream =
            object : ServletOutputStream() {
                override fun write(b: Int) {
                    writing.countDown()
                    if (blockWrites) release.await(WAIT_SECONDS, TimeUnit.SECONDS)
                    body.write(b)
                }

                override fun isReady() = true

                override fun setWriteListener(listener: WriteListener) = Unit
            }
    }
}

private const val WAIT_SECONDS = 5L
private const val BLOCK_MILLIS = 200L

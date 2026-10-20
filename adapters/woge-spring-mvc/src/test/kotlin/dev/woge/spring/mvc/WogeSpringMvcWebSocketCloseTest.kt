package dev.woge.spring.mvc

import dev.woge.host.WebSocketAuthorizer
import dev.woge.host.WebSocketLimits
import dev.woge.host.WebSocketUpgradePolicy
import dev.woge.tck.CursorWebSocketFixture
import dev.woge.tck.tckActionContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.web.socket.BinaryMessage
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketMessage
import org.springframework.web.socket.WebSocketSession
import java.lang.reflect.Proxy
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class WogeSpringMvcWebSocketCloseTest {
    @Test
    fun `container close callback does not interrupt the policy close frame`() {
        val handler = handler()
        val finished = CompletableFuture<Pair<CloseStatus, Boolean>>()
        val session =
            session { socket, status ->
                // Servlet containers can call back before their close operation has returned.
                handler.afterConnectionClosed(socket, status)
                finished.complete(status to Thread.currentThread().isInterrupted)
            }
        rejectUnknownSubscription(handler, session)
        val (status, interrupted) = finished.get(5, TimeUnit.SECONDS)
        assertEquals(CloseStatus.POLICY_VIOLATION.code, status.code)
        assertFalse(interrupted, "Connection cleanup interrupted the container while it was completing the close frame")
    }

    @Test
    fun `a blocked container close still has a deadline`() {
        val handler = handler(TEST_LIMITS)
        val interrupted = CompletableFuture<Boolean>()
        val blocked = CountDownLatch(1)
        val session =
            session { socket, status ->
                handler.afterConnectionClosed(socket, status)
                try {
                    blocked.await()
                    interrupted.complete(false)
                } catch (_: InterruptedException) {
                    interrupted.complete(true)
                }
            }
        try {
            rejectUnknownSubscription(handler, session)
            assertTrue(interrupted.get(5, TimeUnit.SECONDS), "A blocked close must be interrupted at its own deadline")
        } finally {
            blocked.countDown()
            handler.afterConnectionClosed(session, CloseStatus.NORMAL)
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `connection cleanup does not interrupt an in-flight data frame`(binary: Boolean) {
        val handler = handler(TEST_LIMITS)
        val finished = CompletableFuture<Boolean>()
        val session =
            session(send = { socket, message ->
                if (binary == (message is BinaryMessage)) {
                    handler.afterConnectionClosed(socket, CloseStatus.NORMAL)
                    finished.complete(Thread.currentThread().isInterrupted)
                }
            }) { _, _ -> }
        startSend(handler, session, binary)
        assertFalse(finished.get(5, TimeUnit.SECONDS), "Connection cleanup interrupted an in-flight data frame")
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `a blocked data frame still has a deadline`(binary: Boolean) {
        val handler = handler(TEST_LIMITS)
        val interrupted = CompletableFuture<Boolean>()
        val blocked = CountDownLatch(1)
        val session =
            session(send = { socket, message ->
                if (binary == (message is BinaryMessage)) {
                    handler.afterConnectionClosed(socket, CloseStatus.NORMAL)
                    try {
                        blocked.await()
                        interrupted.complete(false)
                    } catch (_: InterruptedException) {
                        interrupted.complete(true)
                    }
                }
            }) { _, _ -> }
        try {
            startSend(handler, session, binary)
            assertTrue(interrupted.get(5, TimeUnit.SECONDS), "A blocked data frame must keep its send deadline")
        } finally {
            blocked.countDown()
            handler.afterConnectionClosed(session, CloseStatus.NORMAL)
        }
    }

    private fun handler(limits: WebSocketLimits = WebSocketLimits()): WogeSpringMvcWebSocket =
        WogeSpringMvcWebSocket(
            WebSocketUpgradePolicy("http://woge.test", WebSocketAuthorizer { _, _ -> true }),
            CursorWebSocketFixture(),
            limits = limits,
        )

    private fun startSend(
        handler: WogeSpringMvcWebSocket,
        session: WebSocketSession,
        binary: Boolean,
    ) {
        handler.afterConnectionEstablished(session)
        if (binary) {
            handler.handleMessage(
                session,
                TextMessage("""{"op":"subscribe","id":"left","epoch":"socket-page","revisions":{"cursor-left":"0"}}"""),
            )
        }
    }

    private fun rejectUnknownSubscription(
        handler: WogeSpringMvcWebSocket,
        session: WebSocketSession,
    ) {
        handler.afterConnectionEstablished(session)
        handler.handleMessage(session, TextMessage("""{"op":"message","id":"unknown","payload":"value"}"""))
    }

    private fun session(
        send: (WebSocketSession, WebSocketMessage<*>) -> Unit = { _, _ -> },
        close: (WebSocketSession, CloseStatus) -> Unit,
    ): WebSocketSession {
        val context = tckActionContext("tck-user", true)
        val attributes = mutableMapOf<String, Any>("woge.socket.context" to context)
        val open = AtomicBoolean(true)
        return Proxy.newProxyInstance(
            WebSocketSession::class.java.classLoader,
            arrayOf(WebSocketSession::class.java),
        ) { proxy, method, arguments ->
            when (method.name) {
                "getId" -> "close-test"
                "getAttributes" -> attributes
                "isOpen" -> open.get()
                "setTextMessageSizeLimit", "setBinaryMessageSizeLimit" -> null
                "sendMessage" -> {
                    send(proxy as WebSocketSession, arguments!![0] as WebSocketMessage<*>)
                    null
                }
                "close" -> {
                    val status = arguments?.firstOrNull() as? CloseStatus ?: CloseStatus.NORMAL
                    open.set(false)
                    close(proxy as WebSocketSession, status)
                    null
                }
                "toString" -> "close-test-session"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === arguments?.firstOrNull()
                else -> error("Unexpected session operation ${method.name}")
            }
        } as WebSocketSession
    }
}

private val TEST_LIMITS = WebSocketLimits(heartbeat = 200.milliseconds, idleTimeout = 2.seconds)

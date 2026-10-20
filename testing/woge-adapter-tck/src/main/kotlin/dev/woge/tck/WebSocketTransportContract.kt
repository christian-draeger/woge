package dev.woge.tck

import dev.woge.protocol.PatchStreamEvent
import dev.woge.protocol.PatchStreamV1
import dev.woge.protocol.ReplacePatch
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** The same real-upgrade, multiplex, bidirectional and revision-resume contract runs on every host. */
internal fun verifyWebSocketTransport(server: AdapterTckServer) {
    HttpClient.newHttpClient().use { client ->
        val url = URI.create(server.origin.toString().replaceFirst("http", "ws") + "/woge-tck/socket")
        for ((origin, subject, proof) in listOf(
            Triple("http://evil.test", "user", "fixture-proof"),
            Triple("http://woge.test", "", "fixture-proof"),
            Triple("http://woge.test", "user", "wrong-proof"),
        )) {
            val denied = runCatching { connectCursor(client, url, CursorListener(), Triple(origin, subject, proof)) }
            check(denied.isFailure) { "WebSocket upgrade admitted an unauthorized handshake" }
        }
        val listener = CursorListener()
        val socket = connectCursor(client, url, listener)
        socket.sendText(cursorSubscribe("left", "cursor-left", 0), true).join()
        socket.sendText(cursorSubscribe("right", "cursor-right", 0), true).join()
        val initial = setOf(listener.patch().first, listener.patch().first)
        check(initial == setOf("left", "right")) { "Socket did not multiplex subscriptions" }
        socket.sendText("""{"op":"message","id":"left","payload":"12,34"}""", true).join()
        val moved = listener.patch()
        check(moved.first == "left" && moved.second.revision.next.value == 2L)
        check(
            moved.second.html.value
                .contains("Cursor: 12,34"),
        )
        socket.sendClose(WebSocket.NORMAL_CLOSURE, "reconnect").join()
        val resumed = CursorListener()
        val connection = connectCursor(client, url, resumed)
        connection.sendText(cursorSubscribe("left", "cursor-left", 1), true).join()
        val replay = resumed.patch().second
        check(replay.revision.base.value == 1L && replay.revision.next.value == 2L)
        connection.sendText("""{"op":"unsubscribe","id":"left"}""", true).join()
        connection.sendText("""{"op":"message","id":"left","payload":"12,34"}""", true).join()
        val closure = resumed.closed.get(SOCKET_WAIT_SECONDS, TimeUnit.SECONDS)
        check(closure.first == SOCKET_POLICY_STATUS) {
            "Unknown subscription must close with $SOCKET_POLICY_STATUS; received ${closure.first}: ${closure.second}"
        }
    }
}

private fun cursorSubscribe(
    id: String,
    target: String,
    revision: Long,
): String = """{"op":"subscribe","id":"$id","epoch":"socket-page","revisions":{"$target":"$revision"}}"""

private fun connectCursor(
    client: HttpClient,
    url: URI,
    listener: CursorListener,
    credentials: Triple<String, String, String> = Triple("http://woge.test", "user", "fixture-proof"),
): WebSocket =
    client
        .newWebSocketBuilder()
        .header("Origin", credentials.first)
        .header("X-Tck-Subject", credentials.second.ifEmpty { "anonymous" })
        .subprotocols("woge.patch.v1", "woge.csrf.${credentials.third}")
        .buildAsync(url, listener)
        .get(SOCKET_WAIT_SECONDS, TimeUnit.SECONDS)

private class CursorListener : WebSocket.Listener {
    val closed = CompletableFuture<Pair<Int, String>>()
    private val frames = LinkedBlockingQueue<ByteArray>()
    private val pending = ByteArrayOutputStream()

    override fun onOpen(webSocket: WebSocket) {
        check(webSocket.subprotocol == "woge.patch.v1")
        webSocket.request(1)
    }

    override fun onBinary(
        webSocket: WebSocket,
        data: ByteBuffer,
        last: Boolean,
    ): CompletableFuture<Void>? {
        val bytes = ByteArray(data.remaining())
        data.get(bytes)
        pending.write(bytes)
        if (last) {
            frames.add(pending.toByteArray())
            pending.reset()
        }
        webSocket.request(1)
        return null
    }

    override fun onText(
        webSocket: WebSocket,
        data: CharSequence,
        last: Boolean,
    ): CompletableFuture<Void>? {
        if (last) webSocket.sendText("""{"op":"pong"}""", true)
        webSocket.request(1)
        return null
    }

    override fun onClose(
        webSocket: WebSocket,
        statusCode: Int,
        reason: String,
    ): CompletableFuture<Void>? {
        closed.complete(statusCode to reason)
        return null
    }

    override fun onError(
        webSocket: WebSocket,
        error: Throwable,
    ) {
        closed.completeExceptionally(error)
    }

    fun patch(): Pair<String, ReplacePatch> {
        val bytes =
            checkNotNull(
                frames.poll(SOCKET_WAIT_SECONDS, TimeUnit.SECONDS),
            ) { "Timed out awaiting a socket patch; closed=${closed.getNow(-1 to "pending")}" }
        val length = bytes[0].toInt()
        val id = bytes.copyOfRange(1, length + 1).toString(Charsets.US_ASCII)
        val decoder = PatchStreamV1.decoder()
        val events = decoder.feed(bytes.copyOfRange(length + 1, bytes.size))
        decoder.finish()
        val patch = (events.first() as PatchStreamEvent.PatchFrame).patch as ReplacePatch
        check((events.last() as PatchStreamEvent.Complete).patchCount == 1)
        return id to patch
    }
}

private const val SOCKET_WAIT_SECONDS = 5L
private const val SOCKET_POLICY_STATUS = 1008

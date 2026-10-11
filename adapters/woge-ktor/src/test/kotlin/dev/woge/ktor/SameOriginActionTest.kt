package dev.woge.ktor

import dev.woge.host.ActionExecutor
import dev.woge.host.FormSubmission
import dev.woge.host.redirect
import dev.woge.html.applicationUrl
import dev.woge.tck.TckActionCommand
import dev.woge.tck.tckActionForm
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class SameOriginActionTest {
    @Test
    fun `default action context verifies same-origin requests and rejects cross-origin or missing evidence`() {
        val handler =
            WogeKtorHandlers().action(
                ActionExecutor<FormSubmission<TckActionCommand>> { redirect(applicationUrl("/done")) },
                tckActionForm.ktorSubmission(),
            )
        val server =
            embeddedServer(Netty, host = "127.0.0.1", port = 0) {
                routing { post("/submit") { handler.handle(call) } }
            }.start(wait = false)
        try {
            val port =
                runBlocking {
                    server.engine
                        .resolvedConnectors()
                        .single()
                        .port
                }
            val origin = URI.create("http://127.0.0.1:$port")
            assertEquals(303, post(origin, origin.toString()).statusCode())
            assertEquals(303, post(origin, null, "same-origin").statusCode())
            assertEquals(403, post(origin, "https://evil.example").statusCode())
            assertEquals(403, post(origin, null).statusCode())
        } finally {
            server.stop(1_000, 1_000)
        }
    }

    private fun post(
        origin: URI,
        originHeader: String?,
        fetchSite: String? = null,
    ): HttpResponse<String> {
        val builder =
            HttpRequest
                .newBuilder(origin.resolve("/submit"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("value=ok"))
        if (originHeader != null) builder.header("Origin", originHeader)
        if (fetchSite != null) builder.header("Sec-Fetch-Site", fetchSite)
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }
}

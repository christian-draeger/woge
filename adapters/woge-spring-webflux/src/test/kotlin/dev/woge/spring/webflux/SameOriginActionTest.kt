package dev.woge.spring.webflux

import dev.woge.host.ActionExecutor
import dev.woge.host.FormSubmission
import dev.woge.host.redirect
import dev.woge.html.applicationUrl
import dev.woge.tck.TckActionCommand
import dev.woge.tck.tckActionForm
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter
import org.springframework.web.reactive.function.server.RouterFunctions
import org.springframework.web.reactive.function.server.coRouter
import reactor.netty.http.server.HttpServer
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class SameOriginActionTest {
    @Test
    fun `default action context verifies same-origin requests and rejects cross-origin or missing evidence`() {
        val action =
            WogeWebFluxHandlers().action(
                ActionExecutor<FormSubmission<TckActionCommand>> { redirect(applicationUrl("/done")) },
                tckActionForm.webFluxSubmission(),
            )
        val routes = coRouter { POST("/submit", action::handle) }
        val server =
            HttpServer
                .create()
                .host("127.0.0.1")
                .port(0)
                .handle(ReactorHttpHandlerAdapter(RouterFunctions.toHttpHandler(routes)))
                .bindNow()
        try {
            val origin = URI.create("http://127.0.0.1:${server.port()}")
            assertEquals(303, post(origin, origin.toString()).statusCode())
            assertEquals(303, post(origin, null, "same-origin").statusCode())
            assertEquals(403, post(origin, "https://evil.example").statusCode())
            assertEquals(403, post(origin, null).statusCode())
        } finally {
            server.disposeNow()
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

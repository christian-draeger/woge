package dev.woge.spring.mvc

import dev.woge.host.ActionExecutor
import dev.woge.host.FormSubmission
import dev.woge.host.redirect
import dev.woge.html.applicationUrl
import dev.woge.tck.TckActionCommand
import dev.woge.tck.tckActionForm
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringApplication
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.web.server.context.ConfigurableWebServerApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class SameOriginActionTest {
    @Test
    fun `default action context verifies same-origin requests and rejects cross-origin or missing evidence`() {
        val context =
            SpringApplication(SameOriginMvcConfiguration::class.java)
                .apply {
                    setDefaultProperties(
                        mapOf("server.address" to "127.0.0.1", "server.port" to "0", "logging.level.root" to "WARN"),
                    )
                }.run() as ConfigurableWebServerApplicationContext
        context.use {
            val origin = URI.create("http://127.0.0.1:${requireNotNull(context.webServer).port}")
            assertEquals(303, post(origin, origin.toString()).statusCode())
            assertEquals(303, post(origin, null, "same-origin").statusCode())
            assertEquals(403, post(origin, "https://evil.example").statusCode())
            assertEquals(403, post(origin, null).statusCode())
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

@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
private class SameOriginMvcConfiguration {
    @Bean
    fun routes(): SimpleUrlHandlerMapping {
        val action =
            WogeSpringMvcHandlers().action(
                ActionExecutor<FormSubmission<TckActionCommand>> { redirect(applicationUrl("/done")) },
                tckActionForm.springMvcSubmission(),
            )
        return SimpleUrlHandlerMapping(mapOf("/submit" to action), 0)
    }
}

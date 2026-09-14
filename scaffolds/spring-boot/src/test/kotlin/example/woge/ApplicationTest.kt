package example.woge

import dev.woge.spring.boot.autoconfigure.WogeRuntimeInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringApplication
import org.springframework.boot.web.server.context.ConfigurableWebServerApplicationContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

public class ApplicationTest {
    @Test
    public fun `serves semantic HTML and ordinary CSS without JavaScript`() {
        SpringApplication(Application::class.java)
            .apply {
                setDefaultProperties(
                    mapOf(
                        "server.port" to "0",
                        "spring.main.banner-mode" to "off",
                        "logging.level.root" to "WARN",
                    ),
                )
            }.run()
            .use { context ->
                val webContext = context as ConfigurableWebServerApplicationContext
                val origin = "http://127.0.0.1:${requireNotNull(webContext.webServer).port}"
                val page = get(origin, "/")

                assertEquals(200, page.statusCode())
                assertTrue(page.headers().firstValue("content-type").orElseThrow().startsWith("text/html"))
                assertTrue(page.body().startsWith("<!doctype html><html lang=\"en\">"))
                assertTrue(page.body().contains("<h1>Hello from Woge</h1>"))
                assertTrue(page.body().contains("<noscript>"))
                assertTrue(page.body().contains("href=\"/styles.css\""))
                assertFalse(page.body().contains("<script"))

                val css = get(origin, "/styles.css")
                assertEquals(200, css.statusCode())
                assertTrue(css.body().contains("@layer reset, theme, page"))
                assertTrue(css.body().contains("@container (width >= 36rem)"))

                val runtimeInfo = context.getBean(WogeRuntimeInfo::class.java)
                assertEquals(
                    requireNotNull(System.getProperty("woge.expected-adapter")),
                    runtimeInfo.adapter.name.lowercase(),
                )
            }
    }

    private fun get(
        origin: String,
        path: String,
    ): HttpResponse<String> =
        CLIENT.send(
            HttpRequest.newBuilder(URI.create(origin + path)).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private companion object {
        private val CLIENT: HttpClient = HttpClient.newHttpClient()
    }
}

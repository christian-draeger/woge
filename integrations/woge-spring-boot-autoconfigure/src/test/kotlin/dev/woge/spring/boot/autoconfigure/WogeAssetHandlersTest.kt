package dev.woge.spring.boot.autoconfigure

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.context.support.StaticApplicationContext
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.server.PathContainer
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.reactive.resource.ResourceWebHandler
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.resource.NoResourceFoundException
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

class WogeAssetHandlersTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `MVC serves actual hashed bytes with immutable caching and refuses unknown hashes`() {
        resources().use { loader ->
            StaticApplicationContext().use { context ->
                context.classLoader = loader
                val registry = MvcRegistry(context)
                WogeSpringMvcAutoConfiguration().wogeSpringMvcAssets().addResourceHandlers(registry)
                val handler = registry.handler()
                val request = MockHttpServletRequest("GET", "/_woge/assets/$HASH/site.css")
                request.setAttribute(
                    org.springframework.web.servlet.HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE,
                    "$HASH/site.css",
                )
                val response = MockHttpServletResponse()
                handler.handleRequest(request, response)
                assertEquals("body {}", response.contentAsString)
                assertEquals(CACHE_CONTROL, response.getHeader(HttpHeaders.CACHE_CONTROL))
                request.setAttribute(
                    org.springframework.web.servlet.HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE,
                    "${"b".repeat(64)}/site.css",
                )
                assertThrows(NoResourceFoundException::class.java) {
                    handler.handleRequest(request, MockHttpServletResponse())
                }
            }
        }
    }

    @Test
    fun `WebFlux serves the same bytes and cache policy and refuses unknown hashes`() {
        resources().use { loader ->
            StaticApplicationContext().use { context ->
                context.classLoader = loader
                val registry = ReactiveRegistry(context)
                WogeWebFluxAutoConfiguration().wogeWebFluxAssets().addResourceHandlers(registry)
                val handler = registry.handler()
                val exchange = exchange("$HASH/site.css")
                handler.handle(exchange).block(Duration.ofSeconds(5))
                assertEquals("body {}", exchange.response.bodyAsString.block(Duration.ofSeconds(5)))
                assertEquals(CACHE_CONTROL, exchange.response.headers.getFirst(HttpHeaders.CACHE_CONTROL))
                val missing =
                    assertThrows(ResponseStatusException::class.java) {
                        handler.handle(exchange("${"b".repeat(64)}/site.css")).block(Duration.ofSeconds(5))
                    }
                assertEquals(HttpStatus.NOT_FOUND, missing.statusCode)
            }
        }
    }

    private fun exchange(path: String): MockServerWebExchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/_woge/assets/$path")).apply {
            attributes[HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE] = PathContainer.parsePath(path)
            attributes[HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE] =
                org.springframework.web.util.pattern
                    .PathPatternParser()
                    .parse("/_woge/assets/**")
        }

    private fun resources(): URLClassLoader {
        val resource = root.resolve("META-INF/woge/assets/$HASH/site.css")
        Files.createDirectories(resource.parent)
        Files.writeString(resource, "body {}")
        return URLClassLoader(arrayOf(root.toUri().toURL()), null)
    }

    private class MvcRegistry(
        context: StaticApplicationContext,
    ) : org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry(context, MockServletContext()) {
        fun handler(): ResourceHttpRequestHandler =
            (requireNotNull(handlerMapping) as org.springframework.web.servlet.handler.SimpleUrlHandlerMapping)
                .urlMap.values
                .single() as ResourceHttpRequestHandler
    }

    private class ReactiveRegistry(
        context: StaticApplicationContext,
    ) : org.springframework.web.reactive.config.ResourceHandlerRegistry(context) {
        fun handler(): ResourceWebHandler =
            (requireNotNull(handlerMapping) as org.springframework.web.reactive.handler.SimpleUrlHandlerMapping)
                .urlMap.values
                .single() as ResourceWebHandler
    }

    private companion object {
        val HASH = "a".repeat(64)
        const val CACHE_CONTROL = "max-age=31536000, public, immutable"
    }
}

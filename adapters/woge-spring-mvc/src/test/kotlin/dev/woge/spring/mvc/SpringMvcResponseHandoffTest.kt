package dev.woge.spring.mvc

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.PageUseCase
import dev.woge.host.ResponseHeaders
import dev.woge.host.ResponseMetadata
import dev.woge.host.htmlPage
import dev.woge.host.httpHeader
import dev.woge.host.redirect
import dev.woge.html.applicationUrl
import jakarta.servlet.DispatcherType
import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponseWrapper
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Regression for #238: Woge must not write the response head while a filter is still writing headers. */
class SpringMvcResponseHandoffTest {
    @Test
    fun `filter headers added after the chain returns never race with Woge headers`() {
        startApplication().use { context ->
            val origin = "http://127.0.0.1:${requireNotNull(context.webServer).port}"
            val client = HttpClient.newHttpClient()
            repeat(ITERATIONS) {
                val redirect = client.get("$origin/redirect")
                assertEquals(303, redirect.statusCode())
                assertEquals("/done", redirect.headers().firstValue("Location").orElse(null))
                redirect.assertAllHeaders()

                val document = client.get("$origin/document")
                assertEquals(200, document.statusCode())
                assertTrue(document.body().contains("handed off"))
                document.assertAllHeaders()
            }
            assertEquals(0, EARLY_WOGE_WRITES.get(), "Woge wrote headers while the original dispatch was running")
        }
    }

    @Test
    fun `pre response failures log one redacted error with the request correlation ID`() {
        startApplication().use { context ->
            val logger = LoggerFactory.getLogger("dev.woge.spring.mvc") as Logger
            val appender = ListAppender<ILoggingEvent>().apply { start() }
            logger.addAppender(appender)
            try {
                val origin = "http://127.0.0.1:${requireNotNull(context.webServer).port}"
                val response = HttpClient.newHttpClient().get("$origin/failure")
                assertEquals(500, response.statusCode())
                assertEquals("", response.body())
                assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow())
                val error = appender.list.single()
                assertEquals(Level.ERROR, error.level)
                assertEquals(
                    "Woge Spring MVC response failed (correlationId=${FAILURE_CORRELATION.get()})",
                    error.formattedMessage,
                )
                assertNull(error.throwableProxy)
            } finally {
                logger.detachAppender(appender)
                appender.stop()
            }
        }
    }

    private fun startApplication(): ConfigurableWebServerApplicationContext =
        SpringApplication(HandoffConfiguration::class.java)
            .apply {
                setDefaultProperties(
                    mapOf(
                        "server.address" to "127.0.0.1",
                        "server.port" to "0",
                        "spring.main.banner-mode" to "off",
                        "logging.level.root" to "WARN",
                    ),
                )
            }.run() as ConfigurableWebServerApplicationContext

    private fun HttpClient.get(url: String): HttpResponse<String> =
        send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString())

    private fun HttpResponse<String>.assertAllHeaders() {
        val missing =
            (0 until HEADER_COUNT).flatMap { listOf("X-Woge-$it", "X-Filter-$it") }.filter {
                headers().firstValue(it).isEmpty
            }
        assertEquals(emptyList<String>(), missing)
    }
}

@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
private class HandoffConfiguration {
    @Bean
    fun lateHeaderFilter(): Filter = LateHeaderFilter()

    @Bean
    fun handoffRoutes(): SimpleUrlHandlerMapping {
        val handlers = WogeSpringMvcHandlers()
        val input = SpringMvcPageInput { request -> request.getAttribute(LATCH_ATTRIBUTE) as CountDownLatch }

        fun page(result: suspend (PageRequest<CountDownLatch>) -> PageResult) =
            handlers.page(
                PageUseCase<CountDownLatch> { request ->
                    // Finish the use case exactly while the filter is still adding its own headers.
                    request.input.await(LATCH_SECONDS, TimeUnit.SECONDS)
                    result(request)
                },
                input,
            )
        return SimpleUrlHandlerMapping(
            mapOf(
                "/redirect" to page { redirect(applicationUrl("/done"), headers = wogeHeaders()) },
                "/document" to page { htmlPage(ResponseMetadata(headers = wogeHeaders())) { text("handed off") } },
                "/failure" to
                    page { request ->
                        FAILURE_CORRELATION.set(request.context.correlationId.value)
                        yield()
                        error("private application failure")
                    },
            ),
            0,
        )
    }

    private fun wogeHeaders(): ResponseHeaders =
        ResponseHeaders.of((0 until HEADER_COUNT).map { httpHeader("X-Woge-$it", "woge") })
}

/** Behaves like Spring Security's HeaderWriterFilter: it writes headers after `chain.doFilter` returns. */
private class LateHeaderFilter : Filter {
    override fun doFilter(
        request: ServletRequest,
        response: ServletResponse,
        chain: FilterChain,
    ) {
        if (request.dispatcherType != DispatcherType.REQUEST) {
            chain.doFilter(request, response)
            return
        }
        val latch = CountDownLatch(1)
        val wogeHeaders = WogeHeaderProbe(response as HttpServletResponse)
        request.setAttribute(LATCH_ATTRIBUTE, latch)
        chain.doFilter(request, wogeHeaders)
        latch.countDown()
        // Gives an unsafe worker time to write; with the handoff Woge cannot write before this filter returns.
        wogeHeaders.writing.await(RACE_WAIT_MILLIS, TimeUnit.MILLISECONDS)
        repeat(HEADER_COUNT) { response.addHeader("X-Filter-$it", "filter") }
        wogeHeaders.dispatchReturned = true
    }
}

private class WogeHeaderProbe(
    response: HttpServletResponse,
) : HttpServletResponseWrapper(response) {
    val writing = CountDownLatch(1)

    @Volatile
    var dispatchReturned = false

    override fun addHeader(
        name: String,
        value: String,
    ) {
        if (name.startsWith("X-Woge-", ignoreCase = true)) {
            if (!dispatchReturned) EARLY_WOGE_WRITES.incrementAndGet()
            writing.countDown()
        }
        super.addHeader(name, value)
    }
}

private val EARLY_WOGE_WRITES = AtomicInteger()

private const val LATCH_ATTRIBUTE = "woge.test.latch"
private const val LATCH_SECONDS = 5L
private const val HEADER_COUNT = 100
private const val ITERATIONS = 40
private const val RACE_WAIT_MILLIS = 5L

private val FAILURE_CORRELATION = AtomicReference<String>()

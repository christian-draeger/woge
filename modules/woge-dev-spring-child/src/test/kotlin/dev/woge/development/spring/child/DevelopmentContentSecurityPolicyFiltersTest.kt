package dev.woge.development.spring.child

import dev.woge.development.ExperimentalWogeDevelopmentApi
import dev.woge.development.client.DevelopmentClientSettings
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

@OptIn(ExperimentalWogeDevelopmentApi::class)
class DevelopmentContentSecurityPolicyFiltersTest {
    private val settings =
        DevelopmentClientSettings(
            eventsUrl = "http://127.0.0.1:4100/events",
            assetsUrl = "http://127.0.0.1:4100",
            detailsUrl = null,
            renderedBuild = null,
            generation = null,
        )
    private val rewriter = DevelopmentPolicyRewriter { settings }
    private val expected = "script-src 'self' http://127.0.0.1:4100"

    @Test
    fun `servlet filter rewrites a policy set later in the chain`() {
        val response = MockHttpServletResponse()
        val chain =
            FilterChain {
                _,
                filtered,
                ->
                (filtered as HttpServletResponse).setHeader(POLICY, "script-src 'self'")
            }

        DevelopmentContentSecurityPolicyServletFilter(rewriter).doFilter(MockHttpServletRequest(), response, chain)

        assertEquals(expected, response.getHeader(POLICY))
    }

    @Test
    fun `web filter rewrites a policy added by a later before-commit action`() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/"))
        val chain =
            WebFilterChain { filtered ->
                filtered.response.beforeCommit {
                    Mono.fromRunnable { filtered.response.headers.set(POLICY, "script-src 'self'") }
                }
                filtered.response.setComplete()
            }

        DevelopmentContentSecurityPolicyWebFilter(rewriter).filter(exchange, chain).block()

        assertEquals(expected, exchange.response.headers.getFirst(POLICY))
    }

    @Test
    fun `without a running session the policy stays unchanged`() {
        assertEquals("script-src 'self'", DevelopmentPolicyRewriter { null }.rewrite(POLICY, "script-src 'self'"))
        assertEquals("text/html", rewriter.rewrite("Content-Type", "text/html"))
    }

    private companion object {
        const val POLICY = "Content-Security-Policy"
    }
}

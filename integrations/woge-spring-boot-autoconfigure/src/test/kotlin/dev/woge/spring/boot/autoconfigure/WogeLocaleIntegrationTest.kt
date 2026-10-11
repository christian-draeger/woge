package dev.woge.spring.boot.autoconfigure

import dev.woge.spring.mvc.DefaultSpringMvcRequestContextFactory
import dev.woge.spring.mvc.SameOriginSpringMvcRequestContextFactory
import dev.woge.spring.webflux.DefaultWebFluxRequestContextFactory
import dev.woge.spring.webflux.SameOriginWebFluxRequestContextFactory
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.context.i18n.SimpleLocaleContext
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.reactive.function.server.HandlerStrategies
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.server.i18n.LocaleContextResolver
import org.springframework.web.servlet.DispatcherServlet
import org.springframework.web.servlet.i18n.FixedLocaleResolver
import java.util.Locale

class WogeLocaleIntegrationTest {
    @Test
    fun `MVC uses Spring locale resolver instead of raw Accept-Language`() {
        val request = MockHttpServletRequest("GET", "/")
        request.addHeader("Accept-Language", "en")
        request.setAttribute(DispatcherServlet.LOCALE_RESOLVER_ATTRIBUTE, FixedLocaleResolver(Locale.FRENCH))
        assertEquals("fr", DefaultSpringMvcRequestContextFactory.create(request).language.value)
        assertEquals("fr", SameOriginSpringMvcRequestContextFactory.create(request).language.value)
    }

    @Test
    fun `WebFlux uses resolved exchange locale instead of raw Accept-Language`() =
        runBlocking {
            val resolver =
                object : LocaleContextResolver {
                    override fun resolveLocaleContext(exchange: org.springframework.web.server.ServerWebExchange) =
                        SimpleLocaleContext(Locale.GERMAN)

                    override fun setLocaleContext(
                        exchange: org.springframework.web.server.ServerWebExchange,
                        localeContext: org.springframework.context.i18n.LocaleContext?,
                    ) = Unit
                }
            val delegate = MockServerWebExchange.from(MockServerHttpRequest.get("/").header("Accept-Language", "en"))
            val exchange =
                object : org.springframework.web.server.ServerWebExchangeDecorator(delegate) {
                    override fun getLocaleContext() = resolver.resolveLocaleContext(this)
                }
            val request = ServerRequest.create(exchange, HandlerStrategies.withDefaults().messageReaders())
            assertEquals("de", DefaultWebFluxRequestContextFactory.create(request).language.value)
            assertEquals("de", SameOriginWebFluxRequestContextFactory.create(request).language.value)
        }
}

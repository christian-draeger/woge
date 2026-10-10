package dev.woge.spring.webflux

import dev.woge.host.FormDecodingException
import dev.woge.host.withFormValidation
import dev.woge.tck.PREPARED_SECURITY_FORM_ATTRIBUTE
import dev.woge.tck.PreparedSecurityForm
import dev.woge.tck.SecurityFormContract
import dev.woge.tck.TckSubmitAction
import dev.woge.tck.tckActionContext
import dev.woge.tck.tckActionValidation
import dev.woge.tck.tckSecurityForm
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.reactor.mono
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatusCode
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService
import org.springframework.security.core.userdetails.User
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.csrf.CsrfToken
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler
import org.springframework.web.reactive.config.EnableWebFlux
import org.springframework.web.reactive.function.server.HandlerStrategies
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.coRouter
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.adapter.WebHttpHandlerBuilder
import reactor.core.publisher.Mono
import reactor.netty.http.server.HttpServer
import java.net.URI

class WebFluxSecurityFormTest {
    @Test
    fun `reactive Security verifies ingress without replacing domain authorization`() {
        AnnotationConfigApplicationContext(WebFluxSecurityConfiguration::class.java).use { context ->
            val server =
                HttpServer
                    .create()
                    .host("127.0.0.1")
                    .port(0)
                    .handle(ReactorHttpHandlerAdapter(WebHttpHandlerBuilder.applicationContext(context).build()))
                    .bindNow()
            try {
                SecurityFormContract().verify(URI.create("http://127.0.0.1:${server.port()}"))
            } finally {
                server.disposeNow()
            }
        }
    }
}

@Configuration(proxyBeanMethods = false)
@EnableWebFlux
@EnableWebFluxSecurity
private class WebFluxSecurityConfiguration {
    @Bean
    fun security(http: ServerHttpSecurity): SecurityWebFilterChain =
        http
            .authorizeExchange { it.anyExchange().authenticated() }
            .httpBasic(Customizer.withDefaults())
            .addFilterBefore(boundedSecurityFormFilter(), SecurityWebFiltersOrder.CSRF)
            .csrf { it.csrfTokenRequestHandler(PreparedCsrfHandler()) }
            .build()

    @Bean
    fun users(): MapReactiveUserDetailsService =
        MapReactiveUserDetailsService(
            listOf("tck-user", "other-user").map {
                User
                    .withUsername(it)
                    .password("{noop}fixture-password")
                    .roles("USER")
                    .build()
            },
        )

    @Bean
    fun routes(): RouterFunction<ServerResponse> {
        val action =
            WogeWebFluxHandlers().action(
                TckSubmitAction.withFormValidation(tckActionValidation),
                WebFluxPageInput { request ->
                    checkNotNull(
                        request.exchange().getAttribute<PreparedSecurityForm>(PREPARED_SECURITY_FORM_ATTRIBUTE),
                    ).submission
                },
                WebFluxRequestContextFactory { request ->
                    tckActionContext(request.principal().awaitSingle().name)
                },
            )
        return coRouter {
            POST(TckSubmitAction.path, action::handle)
            GET("/csrf") { request ->
                val token = checkNotNull(request.exchange().getAttribute<Mono<CsrfToken>>(CsrfToken::class.java.name))
                ServerResponse.ok().bodyValueAndAwait(token.awaitSingle().token)
            }
        }
    }
}

/** Reads a header or a bounded native field, never Spring's unbounded getFormData collector. */
private class PreparedCsrfHandler : ServerCsrfTokenRequestAttributeHandler() {
    override fun resolveCsrfTokenValue(
        exchange: ServerWebExchange,
        csrfToken: CsrfToken,
    ): Mono<String> =
        Mono.justOrEmpty(
            exchange.request.headers.getFirst(csrfToken.headerName)
                ?: exchange.getAttribute<PreparedSecurityForm>(PREPARED_SECURITY_FORM_ATTRIBUTE)?.csrfToken,
        )
}

private fun boundedSecurityFormFilter(): WebFilter =
    WebFilter { exchange, chain ->
        if (exchange.request.method.name() != "POST" || exchange.request.path.value() != TckSubmitAction.path) {
            chain.filter(exchange)
        } else {
            mono {
                val prepared =
                    try {
                        val request = ServerRequest.create(exchange, HandlerStrategies.withDefaults().messageReaders())
                        PreparedSecurityForm(tckSecurityForm.webFluxSubmission().decode(request))
                    } catch (error: FormDecodingException) {
                        exchange.response.statusCode = HttpStatusCode.valueOf(error.category.status.code)
                        return@mono
                    }
                exchange.attributes[PREPARED_SECURITY_FORM_ATTRIBUTE] = prepared
                chain.filter(exchange).awaitSingleOrNull()
            }.then()
        }
    }

package dev.woge.spring.webflux

import dev.woge.host.withFormValidation
import dev.woge.tck.SecurityFormContract
import dev.woge.tck.TckSubmitAction
import dev.woge.tck.tckActionContext
import dev.woge.tck.tckActionForm
import dev.woge.tck.tckActionValidation
import kotlinx.coroutines.reactor.awaitSingle
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService
import org.springframework.security.core.userdetails.User
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.csrf.CsrfToken
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler
import org.springframework.web.reactive.config.EnableWebFlux
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.coRouter
import org.springframework.web.server.ServerWebExchange
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
            .csrf { it.csrfTokenRequestHandler(HeaderCsrfHandler()) }
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
                tckActionForm.webFluxSubmission(),
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

/** Header-only policy avoids Spring's default full-body getFormData collection before Woge. */
private class HeaderCsrfHandler : ServerCsrfTokenRequestAttributeHandler() {
    override fun resolveCsrfTokenValue(
        exchange: ServerWebExchange,
        csrfToken: CsrfToken,
    ): Mono<String> = Mono.justOrEmpty(exchange.request.headers.getFirst(csrfToken.headerName))
}

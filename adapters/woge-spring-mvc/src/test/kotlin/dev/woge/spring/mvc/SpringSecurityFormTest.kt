package dev.woge.spring.mvc

import dev.woge.host.withFormValidation
import dev.woge.tck.SecurityFormContract
import dev.woge.tck.TckSubmitAction
import dev.woge.tck.tckActionContext
import dev.woge.tck.tckActionForm
import dev.woge.tck.tckActionValidation
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringApplication
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.web.server.context.ConfigurableWebServerApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.userdetails.User
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.HttpRequestHandler
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping
import java.net.URI

class SpringSecurityFormTest {
    @Test
    fun `Spring Security verifies ingress but domain authorization stays in the action`() {
        val context =
            SpringApplication(SecurityFormConfiguration::class.java)
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
        context.use {
            SecurityFormContract().verify(URI.create("http://127.0.0.1:${requireNotNull(context.webServer).port}"))
        }
    }
}

@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
@EnableWebSecurity
private class SecurityFormConfiguration {
    @Bean
    fun security(http: HttpSecurity): SecurityFilterChain =
        http
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .httpBasic(Customizer.withDefaults())
            .build()

    @Bean
    fun users(): InMemoryUserDetailsManager =
        InMemoryUserDetailsManager(
            listOf("tck-user", "other-user").map {
                User
                    .withUsername(it)
                    .password("{noop}fixture-password")
                    .roles("USER")
                    .build()
            },
        )

    @Bean
    fun routes(): SimpleUrlHandlerMapping {
        val action =
            WogeSpringMvcHandlers().action(
                TckSubmitAction.withFormValidation(tckActionValidation),
                tckActionForm.springMvcSubmission(),
                SpringMvcRequestContextFactory { request ->
                    // Only reached after this configuration's Authentication and CsrfFilter.
                    tckActionContext(checkNotNull(request.userPrincipal).name)
                },
            )
        val csrf =
            HttpRequestHandler { request, response ->
                val token = request.getAttribute(CsrfToken::class.java.name) as CsrfToken
                response.contentType = "text/plain"
                response.writer.write(token.token)
            }
        return SimpleUrlHandlerMapping(mapOf(TckSubmitAction.path to action, "/csrf" to csrf), 0)
    }
}

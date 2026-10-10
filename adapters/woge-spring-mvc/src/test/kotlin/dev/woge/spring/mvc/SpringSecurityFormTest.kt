package dev.woge.spring.mvc

import dev.woge.host.FormDecodingException
import dev.woge.host.withFormValidation
import dev.woge.tck.PREPARED_SECURITY_FORM_ATTRIBUTE
import dev.woge.tck.PreparedSecurityForm
import dev.woge.tck.SecurityFormContract
import dev.woge.tck.TckSubmitAction
import dev.woge.tck.tckActionContext
import dev.woge.tck.tckActionValidation
import dev.woge.tck.tckSecurityAction
import dev.woge.tck.tckSecurityForm
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
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
import org.springframework.security.web.csrf.CsrfFilter
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.HttpRequestHandler
import org.springframework.web.filter.OncePerRequestFilter
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
            .addFilterBefore(BoundedSecurityFormFilter(), CsrfFilter::class.java)
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
                tckSecurityAction().withFormValidation(tckActionValidation),
                SpringMvcPageInput { request ->
                    (request.getAttribute(PREPARED_SECURITY_FORM_ATTRIBUTE) as PreparedSecurityForm).submission
                },
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

        val expire =
            HttpRequestHandler { request, response ->
                request.getSession(false)?.invalidate()
                response.status = 200
            }
        return SimpleUrlHandlerMapping(
            mapOf(TckSubmitAction.path to action, "/csrf" to csrf, "/expire-tck-session" to expire),
            0,
        )
    }
}

private class BoundedSecurityFormFilter : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.method != "POST" || request.requestURI != TckSubmitAction.path

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val prepared =
            try {
                PreparedSecurityForm(tckSecurityForm.springMvcSubmission().decode(request))
            } catch (error: FormDecodingException) {
                response.sendError(error.category.status.code)
                return
            }
        request.setAttribute(PREPARED_SECURITY_FORM_ATTRIBUTE, prepared)
        filterChain.doFilter(
            object : HttpServletRequestWrapper(request) {
                override fun getParameter(name: String): String? =
                    if (name == "_csrf") prepared.csrfToken else super.getParameter(name)
            },
            response,
        )
    }
}

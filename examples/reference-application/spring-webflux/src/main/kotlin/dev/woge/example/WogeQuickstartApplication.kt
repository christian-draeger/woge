package dev.woge.example

import dev.woge.example.project.ProjectPage
import dev.woge.example.project.REFERENCE_CONTENT_SECURITY_POLICY
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.web.server.WebFilter

/** Starts the maintained Woge quickstart on Spring Boot WebFlux. */
@SpringBootApplication(proxyBeanMethods = false)
public class WogeQuickstartApplication {
    /** Sends the same strict policy on pages, action responses, patches and SSE. */
    @Bean
    public fun contentSecurityPolicy(): WebFilter =
        WebFilter { exchange, chain ->
            exchange.response.headers.set("Content-Security-Policy", REFERENCE_CONTENT_SECURITY_POLICY)
            chain.filter(exchange)
        }

    /** Portable application entry point discovered by Woge's Spring Boot integration. */
    @Bean
    public fun projectPage(): ProjectPage = ProjectPage()
}

/** Runs the quickstart with Spring Boot's normal application lifecycle. */
@Suppress("SpreadOperator")
public fun main(args: Array<String>) {
    runApplication<WogeQuickstartApplication>(*args)
}

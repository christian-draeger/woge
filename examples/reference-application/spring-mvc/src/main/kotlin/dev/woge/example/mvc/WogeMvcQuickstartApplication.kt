package dev.woge.example.mvc

import dev.woge.example.project.ProjectPage
import dev.woge.example.project.REFERENCE_CONTENT_SECURITY_POLICY
import jakarta.servlet.Filter
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean

/** Starts the maintained Woge quickstart on Spring Boot MVC. */
@SpringBootApplication(proxyBeanMethods = false)
public class WogeMvcQuickstartApplication {
    /** Sends the same strict policy on pages, action responses, patches and SSE. */
    @Bean
    public fun contentSecurityPolicy(): Filter =
        Filter { request, response, chain ->
            (response as HttpServletResponse).setHeader("Content-Security-Policy", REFERENCE_CONTENT_SECURITY_POLICY)
            chain.doFilter(request, response)
        }

    /** Portable application entry point shared unchanged with the WebFlux launcher. */
    @Bean
    public fun projectPage(): ProjectPage = ProjectPage()
}

/** Runs the MVC quickstart with Spring Boot's normal application lifecycle. */
@Suppress("SpreadOperator")
public fun main(args: Array<String>) {
    runApplication<WogeMvcQuickstartApplication>(*args)
}

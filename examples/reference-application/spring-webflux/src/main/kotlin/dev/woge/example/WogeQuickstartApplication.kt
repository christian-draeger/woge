package dev.woge.example

import dev.woge.example.project.ProjectPage
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean

/** Starts the maintained Woge quickstart on Spring Boot WebFlux. */
@SpringBootApplication(proxyBeanMethods = false)
public class WogeQuickstartApplication {
    /** Portable application entry point discovered by Woge's Spring Boot integration. */
    @Bean
    public fun projectPage(): ProjectPage = ProjectPage()
}

/** Runs the quickstart with Spring Boot's normal application lifecycle. */
@Suppress("SpreadOperator")
public fun main(args: Array<String>) {
    runApplication<WogeQuickstartApplication>(*args)
}

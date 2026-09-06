package dev.woge.example.mvc

import dev.woge.example.project.ProjectPage
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean

/** Starts the maintained Woge quickstart on Spring Boot MVC. */
@SpringBootApplication(proxyBeanMethods = false)
public class WogeMvcQuickstartApplication {
    /** Portable application entry point shared unchanged with the WebFlux launcher. */
    @Bean
    public fun projectPage(): ProjectPage = ProjectPage()
}

/** Runs the MVC quickstart with Spring Boot's normal application lifecycle. */
@Suppress("SpreadOperator")
public fun main(args: Array<String>) {
    runApplication<WogeMvcQuickstartApplication>(*args)
}

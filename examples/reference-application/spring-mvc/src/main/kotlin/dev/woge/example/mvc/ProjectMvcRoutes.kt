package dev.woge.example.mvc

import dev.woge.example.project.ProjectPage
import dev.woge.example.project.ProjectPageInput
import dev.woge.example.project.ProjectPageView
import dev.woge.spring.mvc.SpringMvcPageInput
import dev.woge.spring.mvc.WogeSpringMvcHandlers
import dev.woge.spring.mvc.pathVariable
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping

/** Connects familiar Spring MVC URL patterns to the framework-neutral project page. */
@Configuration(proxyBeanMethods = false)
public class ProjectMvcRoutes {
    /** Keeps application paths and query-string behavior visible at the host boundary. */
    @Bean
    public fun projectHttpRoutes(
        projectPage: ProjectPage,
        handlers: WogeSpringMvcHandlers,
    ): SimpleUrlHandlerMapping {
        val page =
            handlers.page(
                projectPage,
                SpringMvcPageInput { request ->
                    ProjectPageInput(
                        project = request.pathVariable("project"),
                        view = parseView(request.getParameter("view").orEmpty()),
                    )
                },
            )
        val patches =
            handlers.deferred(
                projectPage,
                SpringMvcPageInput { request -> ProjectPageInput(request.pathVariable("project")) },
            )

        return SimpleUrlHandlerMapping(
            mapOf(
                "/projects/{project}" to page,
                "/projects/{project}/woge-patches" to patches,
            ),
            0,
        )
    }
}

private fun parseView(value: String): ProjectPageView =
    when (value) {
        "" -> ProjectPageView.SHELL
        "complete" -> ProjectPageView.COMPLETE
        else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown project view")
    }

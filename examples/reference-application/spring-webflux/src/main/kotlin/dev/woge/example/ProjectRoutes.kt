package dev.woge.example

import dev.woge.example.project.ProjectPage
import dev.woge.example.project.ProjectPageInput
import dev.woge.example.project.ProjectPageView
import dev.woge.spring.webflux.WebFluxPageInput
import dev.woge.spring.webflux.WogeWebFluxHandlers
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.coRouter
import org.springframework.web.server.ResponseStatusException

/** Connects normal HTTP routes to the framework-neutral project page. */
@Configuration(proxyBeanMethods = false)
public class ProjectRoutes {
    /** Keeps application paths and query-string behavior visible at the host boundary. */
    @Bean
    public fun projectHttpRoutes(
        projectPage: ProjectPage,
        handlers: WogeWebFluxHandlers,
    ): RouterFunction<ServerResponse> {
        val pageInput =
            WebFluxPageInput<ProjectPageInput> { request ->
                ProjectPageInput(
                    project = request.pathVariable("project"),
                    view = parseView(request.queryParam("view").orElse("")),
                )
            }
        val patchInput =
            WebFluxPageInput<ProjectPageInput> { request ->
                ProjectPageInput(request.pathVariable("project"))
            }
        val page = handlers.page(projectPage, pageInput)
        val patches = handlers.deferred(projectPage, patchInput)

        return coRouter {
            GET("/projects/{project}", page::handle)
            GET("/projects/{project}/woge-patches", patches::handle)
        }
    }
}

private fun parseView(value: String): ProjectPageView =
    when (value) {
        "" -> ProjectPageView.SHELL
        "complete" -> ProjectPageView.COMPLETE
        else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown project view")
    }

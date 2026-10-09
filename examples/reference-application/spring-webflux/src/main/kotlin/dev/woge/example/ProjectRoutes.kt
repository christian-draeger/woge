package dev.woge.example

import dev.woge.example.project.ProjectPage
import dev.woge.example.project.ProjectPageRoute
import dev.woge.spring.webflux.WogeWebFluxHandlers
import dev.woge.spring.webflux.webFluxInput
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.coRouter

/** Connects normal HTTP routes to the framework-neutral project page. */
@Configuration(proxyBeanMethods = false)
public class ProjectRoutes {
    /** Keeps application paths visible at the host boundary; the generated route reads the values. */
    @Bean
    public fun projectHttpRoutes(
        projectPage: ProjectPage,
        handlers: WogeWebFluxHandlers,
    ): RouterFunction<ServerResponse> {
        val page = handlers.page(projectPage, ProjectPageRoute)
        val patches = handlers.deferred(projectPage, ProjectPageRoute.webFluxInput())

        return coRouter {
            GET(ProjectPageRoute.path, page::handle)
            GET("${ProjectPageRoute.path}/woge-patches", patches::handle)
        }
    }
}

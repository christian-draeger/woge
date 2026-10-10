package dev.woge.example.mvc

import dev.woge.example.project.ProjectPage
import dev.woge.example.project.ProjectPageRoute
import dev.woge.spring.mvc.WogeSpringMvcHandlers
import dev.woge.spring.mvc.springMvcInput
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping

/** Connects familiar Spring MVC URL patterns to the framework-neutral project page. */
@Configuration(proxyBeanMethods = false)
public class ProjectMvcRoutes {
    /** Keeps application paths visible at the host boundary; the generated route reads the values. */
    @Bean
    public fun projectHttpRoutes(
        projectPage: ProjectPage,
        handlers: WogeSpringMvcHandlers,
    ): SimpleUrlHandlerMapping =
        SimpleUrlHandlerMapping(
            mapOf(
                ProjectPageRoute.path to handlers.page(projectPage, ProjectPageRoute),
                "${ProjectPageRoute.path}/woge-patches" to
                    handlers.deferred(projectPage, ProjectPageRoute.springMvcInput()),
            ),
            0,
        )
}

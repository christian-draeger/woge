package dev.woge.example

import dev.woge.example.project.AddBoardTaskAction
import dev.woge.example.project.ProjectPage
import dev.woge.example.project.ProjectPageRoute
import dev.woge.example.project.ProjectPatchesRoute
import dev.woge.example.project.TaskBoard
import dev.woge.example.project.TaskBoardRoute
import dev.woge.example.project.boardActionContext
import dev.woge.example.project.boardTaskForm
import dev.woge.spring.webflux.WebFluxRequestContextFactory
import dev.woge.spring.webflux.WogeWebFluxHandlers
import dev.woge.spring.webflux.webFluxInput
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.buildAndAwait
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
        val patches = handlers.deferred(projectPage, ProjectPatchesRoute.webFluxInput())

        return coRouter {
            GET(ProjectPageRoute.path, page::handle)
            GET(ProjectPatchesRoute.path, patches::handle)
        }
    }

    @Bean
    public fun taskBoardRoutes(handlers: WogeWebFluxHandlers): RouterFunction<ServerResponse> {
        val board = TaskBoard()
        val page = handlers.page(board.page, TaskBoardRoute)
        val action =
            handlers.action(
                board.action,
                boardTaskForm.webFluxInput(),
                WebFluxRequestContextFactory { boardActionContext() },
            )
        return coRouter {
            GET(TaskBoardRoute.path, page::handle)
            POST(AddBoardTaskAction.path) { request ->
                val uri = request.uri()
                val origin = "${uri.scheme}://${uri.rawAuthority}"
                if (request.headers().header("Origin") != listOf(origin)) {
                    ServerResponse.status(HttpStatus.FORBIDDEN).buildAndAwait()
                } else {
                    action.handle(request)
                }
            }
        }
    }
}

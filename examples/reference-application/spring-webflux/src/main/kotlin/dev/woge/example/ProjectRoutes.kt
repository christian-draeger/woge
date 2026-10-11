package dev.woge.example

import dev.woge.example.project.AddBoardTaskAction
import dev.woge.example.project.BoardActivityRoute
import dev.woge.example.project.BoardLiveRoute
import dev.woge.example.project.BoardRegionRoute
import dev.woge.example.project.ProjectPage
import dev.woge.example.project.ProjectPageRoute
import dev.woge.example.project.ProjectPatchesRoute
import dev.woge.example.project.TaskBoard
import dev.woge.example.project.TaskBoardRoute
import dev.woge.example.project.boardTaskForm
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
        val refresh = handlers.page(board.refresh, BoardRegionRoute)
        val activity = handlers.page(board.activity, BoardActivityRoute)
        val live = handlers.live(board.live, BoardLiveRoute)
        val action =
            handlers.action(board.action, boardTaskForm.webFluxInput())
        return coRouter {
            GET(TaskBoardRoute.path, page::handle)
            GET(BoardRegionRoute.path, refresh::handle)
            GET(BoardActivityRoute.path, activity::handle)
            GET(BoardLiveRoute.path, live::handle)
            POST(AddBoardTaskAction.path, action::handle)
        }
    }
}

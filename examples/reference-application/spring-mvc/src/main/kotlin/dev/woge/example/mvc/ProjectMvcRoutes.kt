package dev.woge.example.mvc

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
                ProjectPatchesRoute.path to
                    handlers.deferred(projectPage, ProjectPatchesRoute.springMvcInput()),
            ),
            0,
        )

    @Bean
    public fun taskBoardRoutes(handlers: WogeSpringMvcHandlers): SimpleUrlHandlerMapping {
        val board = TaskBoard()
        val action = handlers.action(board.action, boardTaskForm.springMvcInput())
        return SimpleUrlHandlerMapping(
            mapOf(
                TaskBoardRoute.path to handlers.page(board.page, TaskBoardRoute),
                BoardRegionRoute.path to handlers.page(board.refresh, BoardRegionRoute),
                BoardActivityRoute.path to handlers.page(board.activity, BoardActivityRoute),
                BoardLiveRoute.path to handlers.live(board.live, BoardLiveRoute),
                AddBoardTaskAction.path to action,
            ),
            0,
        )
    }
}

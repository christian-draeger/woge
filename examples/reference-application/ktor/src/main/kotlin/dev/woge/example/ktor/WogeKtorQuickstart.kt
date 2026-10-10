package dev.woge.example.ktor

import dev.woge.example.project.AddBoardTaskAction
import dev.woge.example.project.BoardRegionRoute
import dev.woge.example.project.ProjectPage
import dev.woge.example.project.ProjectPageRoute
import dev.woge.example.project.ProjectPatchesRoute
import dev.woge.example.project.TaskBoard
import dev.woge.example.project.TaskBoardRoute
import dev.woge.example.project.boardActionContext
import dev.woge.example.project.boardTaskForm
import dev.woge.ktor.KtorRequestContextFactory
import dev.woge.ktor.WogeKtorHandlers
import dev.woge.ktor.ktorInput
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.post
import io.ktor.server.routing.routing

/** Installs the Ktor routes around the same portable page used by both Spring Boot examples. */
public fun Application.wogeReferenceModule() {
    val projectPage = ProjectPage()
    val handlers = WogeKtorHandlers()
    val page = handlers.page(projectPage, ProjectPageRoute)
    val patches = handlers.deferred(projectPage, ProjectPatchesRoute.ktorInput())
    val board = TaskBoard()
    val boardPage = handlers.page(board.page, TaskBoardRoute)
    val boardRefresh = handlers.page(board.refresh, BoardRegionRoute)
    val boardAction =
        handlers.action(
            board.action,
            boardTaskForm.ktorInput(),
            KtorRequestContextFactory { boardActionContext() },
        )

    routing {
        get(ProjectPageRoute.path) { page.handle(call) }
        head(ProjectPageRoute.path) { page.handle(call) }
        get(ProjectPatchesRoute.path) { patches.handle(call) }
        get(TaskBoardRoute.path) { boardPage.handle(call) }
        get(BoardRegionRoute.path) { boardRefresh.handle(call) }
        post(AddBoardTaskAction.path) {
            val connection = call.request.local
            val origin = "${connection.scheme}://${call.request.headers["Host"]}"
            if (call.request.headers.getAll("Origin") != listOf(origin)) {
                call.respond(HttpStatusCode.Forbidden)
            } else {
                boardAction.handle(call)
            }
        }
        staticResources("/assets", "static/assets")
    }
}

/** Runs the Ktor portability example with Netty on port 8080. */
public fun main() {
    val port = System.getenv("PORT")?.toInt() ?: DEFAULT_HTTP_PORT
    require(port in 1..MAX_TCP_PORT) { "PORT must be a valid TCP port" }
    embeddedServer(Netty, host = "0.0.0.0", port = port, module = Application::wogeReferenceModule)
        .start(wait = true)
}

private const val DEFAULT_HTTP_PORT = 8080
private const val MAX_TCP_PORT = 65535

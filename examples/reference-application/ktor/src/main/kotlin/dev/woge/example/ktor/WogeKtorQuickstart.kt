package dev.woge.example.ktor

import dev.woge.example.deployment.DeploymentConfirmAction
import dev.woge.example.deployment.DeploymentLiveRoute
import dev.woge.example.deployment.DeploymentRoute
import dev.woge.example.deployment.ReferenceOperations
import dev.woge.example.deployment.deploymentForm
import dev.woge.example.deployment.deploymentLive
import dev.woge.example.deployment.deploymentPage
import dev.woge.example.notes.AddNoteAction
import dev.woge.example.notes.NotesPage
import dev.woge.example.notes.NotesRoute
import dev.woge.example.notes.addNoteForm
import dev.woge.example.project.AddBoardTaskAction
import dev.woge.example.project.BoardActivityRoute
import dev.woge.example.project.BoardLiveRoute
import dev.woge.example.project.BoardRegionRoute
import dev.woge.example.project.ProjectPage
import dev.woge.example.project.ProjectPageRoute
import dev.woge.example.project.ProjectPatchesRoute
import dev.woge.example.project.REFERENCE_CONTENT_SECURITY_POLICY
import dev.woge.example.project.TaskBoard
import dev.woge.example.project.TaskBoardRoute
import dev.woge.example.project.boardTaskForm
import dev.woge.ktor.WogeKtorHandlers
import dev.woge.ktor.ktorInput
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.util.concurrent.atomic.AtomicBoolean

/** Installs the Ktor routes around the same portable page used by both Spring Boot examples. */
public fun Application.wogeReferenceModule() {
    intercept(ApplicationCallPipeline.Setup) {
        call.response.headers.append("Content-Security-Policy", REFERENCE_CONTENT_SECURITY_POLICY)
    }
    val projectPage = ProjectPage()
    val deploymentEnabled = System.getenv("WOGE_DEPLOYMENT") == "true"
    val handlers =
        if (deploymentEnabled) deploymentHandlers() else WogeKtorHandlers()
    if (deploymentEnabled) installDeploymentRoutes(handlers)
    val page = handlers.page(projectPage, ProjectPageRoute)
    val patches = handlers.deferred(projectPage, ProjectPatchesRoute.ktorInput())
    val notes = handlers.page(NotesPage(), NotesRoute)
    val addNote = handlers.action(AddNoteAction, addNoteForm.ktorInput())
    val board = TaskBoard()
    val boardPage = handlers.page(board.page, TaskBoardRoute)
    val boardRefresh = handlers.page(board.refresh, BoardRegionRoute)
    val boardActivity = handlers.page(board.activity, BoardActivityRoute)
    val boardLive = handlers.live(board.live, BoardLiveRoute)
    val boardAction =
        handlers.action(board.action, boardTaskForm.ktorInput())

    routing {
        get(NotesRoute.path) { notes.handle(call) }
        head(NotesRoute.path) { notes.handle(call) }
        post(AddNoteAction.path) { addNote.handle(call) }
        get(ProjectPageRoute.path) { page.handle(call) }
        head(ProjectPageRoute.path) { page.handle(call) }
        get(ProjectPatchesRoute.path) { patches.handle(call) }
        get(TaskBoardRoute.path) { boardPage.handle(call) }
        get(BoardRegionRoute.path) { boardRefresh.handle(call) }
        get(BoardActivityRoute.path) { boardActivity.handle(call) }
        get(BoardLiveRoute.path) { boardLive.handle(call) }
        post(AddBoardTaskAction.path) { boardAction.handle(call) }
        staticResources("/assets", "static/assets")
    }
}

/** Runs the Ktor portability example with Netty on port 8080. */
public fun main() {
    val port = System.getenv("PORT")?.toInt() ?: DEFAULT_HTTP_PORT
    require(port in 1..MAX_TCP_PORT) { "PORT must be a valid TCP port" }
    embeddedServer(
        Netty,
        host = "0.0.0.0",
        port = port,
        module = Application::wogeReferenceModule,
    ).apply {
        engineConfig.shutdownGracePeriod = SHUTDOWN_GRACE_MILLIS
        engineConfig.shutdownTimeout = SHUTDOWN_TIMEOUT_MILLIS
    }.start(wait = true)
}

private const val DEFAULT_HTTP_PORT = 8080
private const val MAX_TCP_PORT = 65535
private const val SHUTDOWN_GRACE_MILLIS = 1000L
private const val SHUTDOWN_TIMEOUT_MILLIS = 25_000L

private fun deploymentHandlers(): WogeKtorHandlers =
    WogeKtorHandlers(
        maxRegions = ReferenceOperations.maxRegions,
        maxConcurrency = ReferenceOperations.maxConcurrency,
        patchStreamLimits = ReferenceOperations.patchLimits,
        liveLimits = ReferenceOperations.liveLimits,
        renderIdentitySecret = ReferenceOperations.identitySecret,
        observer = ReferenceOperations.observer,
    )

private fun Application.installDeploymentRoutes(handlers: WogeKtorHandlers) {
    val ready = AtomicBoolean(true)
    monitor.subscribe(ApplicationStopping) { ready.set(false) }
    val deployment = handlers.page(deploymentPage, DeploymentRoute)
    val confirm = handlers.action(DeploymentConfirmAction, deploymentForm.ktorInput())
    val deploymentEvents = handlers.live(deploymentLive, DeploymentLiveRoute)
    routing {
        get("/health/live") { call.respondText("UP") }
        get("/health/ready") {
            val accepting = ready.get()
            call.respondText(
                if (accepting) "READY" else "NOT_READY",
                status = if (accepting) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
            )
        }
        get(DeploymentRoute.path) { deployment.handle(call) }
        post(DeploymentConfirmAction.path) { confirm.handle(call) }
        get(DeploymentLiveRoute.path) { deploymentEvents.handle(call) }
    }
}

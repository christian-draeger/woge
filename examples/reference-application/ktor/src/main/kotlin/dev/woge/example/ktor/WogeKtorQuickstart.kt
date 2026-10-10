package dev.woge.example.ktor

import dev.woge.example.project.ProjectPage
import dev.woge.example.project.ProjectPageRoute
import dev.woge.ktor.WogeKtorHandlers
import dev.woge.ktor.ktorInput
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.routing

/** Installs the Ktor routes around the same portable page used by both Spring Boot examples. */
public fun Application.wogeReferenceModule() {
    val projectPage = ProjectPage()
    val handlers = WogeKtorHandlers()
    val page = handlers.page(projectPage, ProjectPageRoute)
    val patches = handlers.deferred(projectPage, ProjectPageRoute.ktorInput())

    routing {
        get(ProjectPageRoute.path) { page.handle(call) }
        head(ProjectPageRoute.path) { page.handle(call) }
        get("${ProjectPageRoute.path}/woge-patches") { patches.handle(call) }
        staticResources("/assets", "static/assets")
    }
}

/** Runs the Ktor portability example with Netty on port 8080. */
public fun main() {
    embeddedServer(Netty, host = "0.0.0.0", port = 8080, module = Application::wogeReferenceModule)
        .start(wait = true)
}

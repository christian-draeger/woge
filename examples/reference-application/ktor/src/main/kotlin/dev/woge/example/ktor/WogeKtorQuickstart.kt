package dev.woge.example.ktor

import dev.woge.example.project.ProjectPage
import dev.woge.example.project.ProjectPageInput
import dev.woge.example.project.ProjectPageView
import dev.woge.ktor.KtorPageInput
import dev.woge.ktor.WogeKtorHandlers
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.routing

/** Installs the Ktor routes around the same portable page used by both Spring Boot examples. */
public fun Application.wogeReferenceModule() {
    val projectPage = ProjectPage()
    val handlers = WogeKtorHandlers()
    val page =
        handlers.page(
            projectPage,
            KtorPageInput { call ->
                ProjectPageInput(
                    project = requireNotNull(call.parameters["project"]),
                    view = parseView(call.request.queryParameters["view"].orEmpty()),
                )
            },
        )
    val patches =
        handlers.deferred(
            projectPage,
            KtorPageInput { call -> ProjectPageInput(requireNotNull(call.parameters["project"])) },
        )

    routing {
        get("/projects/{project}") { page.handle(call) }
        head("/projects/{project}") { page.handle(call) }
        get("/projects/{project}/woge-patches") { patches.handle(call) }
        staticResources("/assets", "static/assets")
    }
}

/** Runs the Ktor portability example with Netty on port 8080. */
public fun main() {
    embeddedServer(Netty, host = "0.0.0.0", port = 8080, module = Application::wogeReferenceModule)
        .start(wait = true)
}

private fun parseView(value: String): ProjectPageView =
    when (value) {
        "" -> ProjectPageView.SHELL
        "complete" -> ProjectPageView.COMPLETE
        else -> throw BadRequestException("Unknown project view")
    }

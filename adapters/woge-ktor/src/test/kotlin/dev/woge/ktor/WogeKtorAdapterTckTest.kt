package dev.woge.ktor

import dev.woge.tck.AdapterTckApplication
import dev.woge.tck.AdapterTckCapability
import dev.woge.tck.AdapterTckDeferredScenario
import dev.woge.tck.AdapterTckFailureRoute
import dev.woge.tck.AdapterTckHarnessFactory
import dev.woge.tck.AdapterTckPageScenario
import dev.woge.tck.AdapterTckRoute
import dev.woge.tck.AdapterTckRoutes
import dev.woge.tck.AdapterTckServer
import dev.woge.tck.ServerAdapterContract
import dev.woge.tck.TckSubmitAction
import dev.woge.tck.tckActionContext
import dev.woge.tck.tckActionForm
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.net.URI

class WogeKtorAdapterTckTest {
    @Test
    fun `Ktor passes the shared server adapter contract`() {
        ServerAdapterContract(KtorTckHarnessFactory).verify()
    }
}

private object KtorTckHarnessFactory : AdapterTckHarnessFactory {
    override val adapterName: String = "ktor"

    override fun start(application: AdapterTckApplication): AdapterTckServer {
        val page =
            WogeKtorHandlers(observer = application.observer).page(
                application.pages,
                KtorPageInput { call ->
                    AdapterTckPageScenario.fromPath(requireNotNull(call.parameters["scenario"]))
                },
            )
        val deferred =
            WogeKtorHandlers(observer = application.observer).deferred(
                application.deferredRegions,
                KtorPageInput { call ->
                    AdapterTckDeferredScenario.fromPath(requireNotNull(call.parameters["scenario"]))
                },
            )
        val route = WogeKtorHandlers(observer = application.observer).page(application.routePages, AdapterTckRoute)
        val failures =
            WogeKtorHandlers(failurePages = application.failurePages)
                .page(application.failureRoutePages, AdapterTckFailureRoute)
        val action =
            WogeKtorHandlers().action(
                TckSubmitAction,
                tckActionForm.ktorInput(),
                KtorRequestContextFactory { call -> tckActionContext(call.request.headers["X-Tck-Subject"]) },
            )
        val server =
            embeddedServer(Netty, host = "127.0.0.1", port = 0) {
                routing {
                    get(AdapterTckRoutes.PAGE_PATTERN) { page.handle(call) }
                    head(AdapterTckRoutes.PAGE_PATTERN) { page.handle(call) }
                    get(AdapterTckRoutes.DEFERRED_PATTERN) { deferred.handle(call) }
                    get(AdapterTckRoute.path) { route.handle(call) }
                    get(AdapterTckFailureRoute.path) { failures.handle(call) }
                    head(AdapterTckFailureRoute.path) { failures.handle(call) }
                    post(TckSubmitAction.path) { action.handle(call) }
                    get(TckSubmitAction.path) { action.handle(call) }
                }
            }.start(wait = false)
        return KtorTckServer(server)
    }
}

private class KtorTckServer(
    private val server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>,
) : AdapterTckServer {
    private val port: Int =
        runBlocking {
            server.engine
                .resolvedConnectors()
                .single()
                .port
        }
    override val origin: URI =
        URI.create("http://127.0.0.1:$port")
    override val capabilities: Set<AdapterTckCapability> = emptySet()

    override fun close() {
        server.stop(1_000, 1_000)
    }
}

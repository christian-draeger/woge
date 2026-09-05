package dev.woge.spring.webflux

import dev.woge.tck.AdapterTckApplication
import dev.woge.tck.AdapterTckCapability
import dev.woge.tck.AdapterTckDeferredScenario
import dev.woge.tck.AdapterTckHarnessFactory
import dev.woge.tck.AdapterTckPageScenario
import dev.woge.tck.AdapterTckRoutes
import dev.woge.tck.AdapterTckServer
import dev.woge.tck.ServerAdapterContract
import org.junit.jupiter.api.Test
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter
import org.springframework.web.reactive.function.server.RouterFunctions
import org.springframework.web.reactive.function.server.coRouter
import reactor.netty.DisposableServer
import reactor.netty.http.server.HttpServer
import java.net.URI

class WogeWebFluxAdapterTckTest {
    @Test
    fun `WebFlux passes the shared server adapter contract`() {
        ServerAdapterContract(WebFluxTckHarnessFactory).verify()
    }
}

private object WebFluxTckHarnessFactory : AdapterTckHarnessFactory {
    override val adapterName: String = "spring-webflux"

    override fun start(application: AdapterTckApplication): AdapterTckServer {
        val page =
            WogeWebFluxPageHandler(
                application.pages,
                WebFluxPageInput { request ->
                    AdapterTckPageScenario.fromPath(request.pathVariable("scenario"))
                },
            )
        val deferred =
            WogeWebFluxDeferredHandler(
                application.deferredRegions,
                WebFluxPageInput { request ->
                    AdapterTckDeferredScenario.fromPath(request.pathVariable("scenario"))
                },
            )
        val routes =
            coRouter {
                GET(AdapterTckRoutes.PAGE_PATTERN, page::handle)
                HEAD(AdapterTckRoutes.PAGE_PATTERN, page::handle)
                GET(AdapterTckRoutes.DEFERRED_PATTERN, deferred::handle)
            }
        return WebFluxTckServer(
            HttpServer
                .create()
                .host("127.0.0.1")
                .port(0)
                .handle(ReactorHttpHandlerAdapter(RouterFunctions.toHttpHandler(routes)))
                .bindNow(),
        )
    }
}

private class WebFluxTckServer(
    private val server: DisposableServer,
) : AdapterTckServer {
    override val origin: URI = URI.create("http://127.0.0.1:${server.port()}")
    override val capabilities: Set<AdapterTckCapability> = setOf(AdapterTckCapability.CLIENT_ABORT_CANCELLATION)

    override fun close() {
        server.disposeNow()
    }
}

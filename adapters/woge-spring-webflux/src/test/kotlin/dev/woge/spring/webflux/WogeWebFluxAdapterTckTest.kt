package dev.woge.spring.webflux

import dev.woge.tck.AdapterTckApplication
import dev.woge.tck.AdapterTckCapability
import dev.woge.tck.AdapterTckDeferredScenario
import dev.woge.tck.AdapterTckFailureRoute
import dev.woge.tck.AdapterTckHarnessFactory
import dev.woge.tck.AdapterTckPageScenario
import dev.woge.tck.AdapterTckRoute
import dev.woge.tck.AdapterTckRoutes
import dev.woge.tck.AdapterTckServer
import dev.woge.tck.NativeFormBrowserContract
import dev.woge.tck.ServerAdapterContract
import dev.woge.tck.TckSubmitAction
import dev.woge.tck.tckActionContext
import dev.woge.tck.tckActionForm
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter
import org.springframework.web.reactive.function.server.RouterFunctions
import org.springframework.web.reactive.function.server.coRouter
import reactor.netty.DisposableServer
import reactor.netty.http.server.HttpServer
import java.net.URI
import java.nio.file.Path

class WogeWebFluxAdapterTckTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "WOGE_NATIVE_BROWSER_SCRIPT", matches = ".+")
    fun `native forms pass the real browser contract`() {
        ServerAdapterContract(WebFluxTckHarnessFactory).verify(
            listOf(NativeFormBrowserContract(Path.of(System.getenv("WOGE_NATIVE_BROWSER_SCRIPT")))),
        )
    }

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
                observer = application.observer,
            )
        val deferred =
            WogeWebFluxDeferredHandler(
                application.deferredRegions,
                WebFluxPageInput { request ->
                    AdapterTckDeferredScenario.fromPath(request.pathVariable("scenario"))
                },
                observer = application.observer,
            )
        val route = WogeWebFluxHandlers(observer = application.observer).page(application.routePages, AdapterTckRoute)
        val failures =
            WogeWebFluxHandlers(failurePages = application.failurePages)
                .page(application.failureRoutePages, AdapterTckFailureRoute)
        val action =
            WogeWebFluxHandlers().action(
                application.actionSubmissions,
                tckActionForm.webFluxSubmission(),
                WebFluxRequestContextFactory { request ->
                    tckActionContext(
                        request.headers().firstHeader("X-Tck-Subject"),
                        verified = request.headers().firstHeader("X-Tck-Unverified") != "true",
                    )
                },
            )
        val complete = WogeWebFluxHandlers().page(application.actionCompletion, WebFluxPageInput { })
        val routes =
            coRouter {
                GET(AdapterTckRoutes.PAGE_PATTERN, page::handle)
                HEAD(AdapterTckRoutes.PAGE_PATTERN, page::handle)
                GET(AdapterTckRoutes.DEFERRED_PATTERN, deferred::handle)
                GET(AdapterTckRoute.path, route::handle)
                GET(AdapterTckFailureRoute.path, failures::handle)
                HEAD(AdapterTckFailureRoute.path, failures::handle)
                POST(TckSubmitAction.path, action::handle)
                GET(TckSubmitAction.path, action::handle)
                GET("/woge-tck/action-complete", complete::handle)
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

package dev.woge.spring.webflux

import dev.woge.tck.AdapterTckApplication
import dev.woge.tck.AdapterTckCapability
import dev.woge.tck.AdapterTckDeferredScenario
import dev.woge.tck.AdapterTckFailureRoute
import dev.woge.tck.AdapterTckHarnessFactory
import dev.woge.tck.AdapterTckLiveRoute
import dev.woge.tck.AdapterTckManagedRoute
import dev.woge.tck.AdapterTckPageScenario
import dev.woge.tck.AdapterTckRoute
import dev.woge.tck.AdapterTckRoutes
import dev.woge.tck.AdapterTckServer
import dev.woge.tck.NativeFormBrowserContract
import dev.woge.tck.ServerAdapterContract
import dev.woge.tck.TckSubmitAction
import dev.woge.tck.TckUploadAction
import dev.woge.tck.tckActionContext
import dev.woge.tck.tckActionForm
import dev.woge.tck.tckUploadForm
import dev.woge.tck.tckUploadPage
import dev.woge.tck.tckUploadPolicy
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
    @EnabledIfEnvironmentVariable(named = "WOGE_DPU_BROWSER_SCRIPT", matches = ".+")
    fun `native DPU passes original document browser conformance`() {
        ServerAdapterContract(WebFluxTckHarnessFactory).verify(
            listOf(dev.woge.tck.NativeDpuBrowserContract(Path.of(System.getenv("WOGE_DPU_BROWSER_SCRIPT")))),
        )
    }

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
                patchStreamLimits = application.deferredPatchStreamLimits,
            )
        val route = WogeWebFluxHandlers(observer = application.observer).page(application.routePages, AdapterTckRoute)
        val managed = WogeWebFluxHandlers().liveRegion(application.managedLive, AdapterTckManagedRoute)
        val live =
            WogeWebFluxHandlers(observer = application.observer, liveLimits = application.liveLimits)
                .live(application.live, AdapterTckLiveRoute)
        val failures =
            WogeWebFluxHandlers(failurePages = application.failurePages)
                .page(application.failureRoutePages, AdapterTckFailureRoute)
        val action = submitAction(application)
        val complete = WogeWebFluxHandlers().page(application.actionCompletion, WebFluxPageInput { })
        val upload = uploadAction(application)
        val uploadPage = WogeWebFluxHandlers().page(tckUploadPage, WebFluxPageInput { })
        val routes =
            coRouter {
                initialDocumentRoutes(application)
                GET(AdapterTckRoutes.PAGE_PATTERN, page::handle)
                HEAD(AdapterTckRoutes.PAGE_PATTERN, page::handle)
                GET(AdapterTckRoutes.DEFERRED_PATTERN, deferred::handle)
                GET(AdapterTckRoute.path, route::handle)
                GET(AdapterTckLiveRoute.path, live::handle)
                GET(AdapterTckManagedRoute.path, managed::handle)
                GET(AdapterTckFailureRoute.path, failures::handle)
                HEAD(AdapterTckFailureRoute.path, failures::handle)
                POST(TckSubmitAction.path, action::handle)
                GET(TckSubmitAction.path, action::handle)
                GET("/woge-tck/action-complete", complete::handle)
                POST(TckUploadAction.path, upload::handle)
                GET("/woge-tck/upload-complete", uploadPage::handle)
            }
        return WebFluxTckServer(
            HttpServer
                .create()
                .host("127.0.0.1")
                .port(0)
                .handle(ReactorHttpHandlerAdapter(withCursorSocket(application, routes)))
                .bindNow(),
        )
    }

    private fun org.springframework.web.reactive.function.server.CoRouterFunctionDsl.initialDocumentRoutes(
        application: AdapterTckApplication,
    ) {
        for ((mode, policy) in listOf(
            "auto" to dev.woge.runtime.NativeDpuMode.AUTO,
            "native" to dev.woge.runtime.NativeDpuMode.FORCE_NATIVE,
            "fallback" to dev.woge.runtime.NativeDpuMode.FORCE_FALLBACK,
        )) {
            val dpu =
                WogeWebFluxHandlers(nativeDpuMode = policy, maxRegions = 2).page(
                    application.initialDocuments.pages,
                    WebFluxPageInput { it.pathVariable("scenario") },
                )
            GET("/woge-tck/dpu/$mode/{scenario}", dpu::handle)
            HEAD("/woge-tck/dpu/$mode/{scenario}", dpu::handle)
        }
        GET("/woge-tck/dpu/complete") {
            dev.woge.host
                .htmlPage {
                    element("ul") {
                        listOf("Alpha", "Beta", "Gamma").forEach { element("li") { text(it) } }
                    }
                }.toWebFluxResponse(dev.woge.host.WogeObserver.NONE, dev.woge.host.WogeObservationContext())
        }
    }

    private fun withCursorSocket(
        application: AdapterTckApplication,
        routes: org.springframework.web.reactive.function.server.RouterFunction<
            org.springframework.web.reactive.function.server.ServerResponse,
        >,
    ): org.springframework.http.server.reactive.HttpHandler {
        val socket =
            WogeWebFluxWebSocket(
                dev.woge.tck.cursorWebSocketPolicy(),
                application.cursorSocket,
                WebFluxRequestContextFactory {
                    tckActionContext(
                        it.headers().firstHeader("X-Tck-Subject")?.takeUnless { subject ->
                            subject ==
                                "anonymous"
                        },
                        true,
                    )
                },
            )
        val ordinary = RouterFunctions.toHttpHandler(routes)
        val socketHttp =
            org.springframework.web.server.adapter.WebHttpHandlerBuilder
                .webHandler(socket)
                .build()
        return org.springframework.http.server.reactive.HttpHandler { request, response ->
            if (request.path.value() == "/woge-tck/socket") {
                socketHttp.handle(request, response)
            } else {
                ordinary.handle(request, response)
            }
        }
    }

    private fun submitAction(application: AdapterTckApplication) =
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

    private fun uploadAction(application: AdapterTckApplication) =
        WogeWebFluxHandlers().action(
            TckUploadAction,
            tckUploadForm.webFluxMultipart(tckUploadPolicy(application.uploadDirectory)),
            WebFluxRequestContextFactory { request ->
                tckActionContext(
                    request.headers().firstHeader("X-Tck-Subject"),
                    request.headers().firstHeader("X-Tck-Unverified") != "true",
                )
            },
        )
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

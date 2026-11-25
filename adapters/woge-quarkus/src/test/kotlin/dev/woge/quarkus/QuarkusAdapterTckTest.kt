package dev.woge.quarkus

import dev.woge.host.PageUseCase
import dev.woge.host.WEB_SOCKET_PROTOCOL
import dev.woge.host.htmlPage
import dev.woge.runtime.DeferredRegionPolicy
import dev.woge.runtime.NativeDpuMode
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
import dev.woge.tck.NativeDpuBrowserContract
import dev.woge.tck.NativeFormBrowserContract
import dev.woge.tck.ServerAdapterContract
import dev.woge.tck.TckSubmitAction
import dev.woge.tck.TckUploadAction
import dev.woge.tck.cursorWebSocketPolicy
import dev.woge.tck.tckActionContext
import dev.woge.tck.tckActionForm
import dev.woge.tck.tckUploadForm
import dev.woge.tck.tckUploadPage
import dev.woge.tck.tckUploadPolicy
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerOptions
import io.vertx.ext.web.Router
import io.vertx.kotlin.coroutines.coAwait
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.net.URI
import java.nio.file.Path

@Timeout(90)
class QuarkusAdapterTckTest {
    @Test
    fun `passes shared server adapter contract over real Vertx HTTP`() {
        ServerAdapterContract(QuarkusTckHarness).verify()
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "WOGE_DPU_BROWSER_SCRIPT", matches = ".+")
    fun `native DPU passes original document browser conformance`() {
        ServerAdapterContract(QuarkusTckHarness).verify(
            listOf(NativeDpuBrowserContract(Path.of(System.getenv("WOGE_DPU_BROWSER_SCRIPT")))),
        )
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "WOGE_NATIVE_BROWSER_SCRIPT", matches = ".+")
    fun `passes shared native form browser contract`() {
        ServerAdapterContract(QuarkusTckHarness).verify(
            listOf(NativeFormBrowserContract(Path.of(System.getenv("WOGE_NATIVE_BROWSER_SCRIPT")))),
        )
    }
}

private object QuarkusTckHarness : AdapterTckHarnessFactory {
    override val adapterName = "quarkus"

    override fun start(application: AdapterTckApplication): AdapterTckServer =
        runBlocking {
            val vertx = Vertx.vertx()
            try {
                val router = Router.router(vertx)
                routes(router, application)
                val options = HttpServerOptions().addWebSocketSubProtocol(WEB_SOCKET_PROTOCOL)
                val server =
                    vertx
                        .createHttpServer(options)
                        .requestHandler(router)
                        .listen(0, "127.0.0.1")
                        .coAwait()
                object : AdapterTckServer {
                    override val origin = URI.create("http://127.0.0.1:${server.actualPort()}")
                    override val capabilities = setOf(AdapterTckCapability.CLIENT_ABORT_CANCELLATION)

                    override fun close() {
                        runBlocking { vertx.close().coAwait() }
                    }
                }
            } catch (failure: Throwable) {
                vertx.close().coAwait()
                throw failure
            }
        }

    private fun routes(
        router: Router,
        application: AdapterTckApplication,
    ) {
        val handlers =
            WogeQuarkusHandlers(
                observer = application.observer,
                patchLimits = application.deferredPatchStreamLimits,
                liveLimits = application.liveLimits,
            )
        val page =
            handlers.page(
                application.pages,
                QuarkusPageInput { AdapterTckPageScenario.fromPath(requireNotNull(it.pathParam("scenario"))) },
            )
        val deferred =
            handlers.deferred(
                application.deferredRegions,
                QuarkusPageInput { AdapterTckDeferredScenario.fromPath(requireNotNull(it.pathParam("scenario"))) },
            )
        val failures =
            WogeQuarkusHandlers(failurePages = application.failurePages)
                .page(application.failureRoutePages, AdapterTckFailureRoute)
        val security =
            QuarkusRequestContextFactory { call ->
                val subject: String? = call.request().getHeader("X-Tck-Subject")
                tckActionContext(
                    subject?.takeUnless { it == "anonymous" },
                    verified = call.request().getHeader("X-Tck-Unverified") != "true",
                )
            }
        val actions = WogeQuarkusHandlers(contexts = security)
        val submit = actions.action(application.actionSubmissions, tckActionForm.quarkusSubmission())
        val upload =
            actions.action(
                TckUploadAction,
                tckUploadForm.quarkusMultipart(tckUploadPolicy(application.uploadDirectory)),
            )

        router.get(AdapterTckRoutes.PAGE_PATTERN.vertxPath()).handler(page)
        router.head(AdapterTckRoutes.PAGE_PATTERN.vertxPath()).handler(page)
        router.get(AdapterTckRoutes.DEFERRED_PATTERN.vertxPath()).handler(deferred)
        router.get(AdapterTckRoute.path.vertxPath()).handler(handlers.page(application.routePages, AdapterTckRoute))
        router.get(AdapterTckLiveRoute.path.vertxPath()).handler(handlers.live(application.live, AdapterTckLiveRoute))
        router
            .get(AdapterTckManagedRoute.path.vertxPath())
            .handler(handlers.liveRegion(application.managedLive, AdapterTckManagedRoute))
        router.get(AdapterTckFailureRoute.path.vertxPath()).handler(failures)
        router.head(AdapterTckFailureRoute.path.vertxPath()).handler(failures)
        router.post(TckSubmitAction.path).handler(submit)
        router.get(TckSubmitAction.path).handler(submit)
        router
            .get("/woge-tck/action-complete")
            .handler(handlers.page(application.actionCompletion, QuarkusPageInput { Unit }))
        router.post(TckUploadAction.path).handler(upload)
        router.get("/woge-tck/upload-complete").handler(handlers.page(tckUploadPage, QuarkusPageInput { Unit }))
        router.get("/woge-tck/socket").handler(
            wogeQuarkusWebSocket(cursorWebSocketPolicy(), application.cursorSocket, security),
        )
        initialDocumentRoutes(router, application)
    }

    private fun initialDocumentRoutes(
        router: Router,
        application: AdapterTckApplication,
    ) {
        for ((mode, policy) in listOf(
            "auto" to NativeDpuMode.AUTO,
            "native" to NativeDpuMode.FORCE_NATIVE,
            "fallback" to NativeDpuMode.FORCE_FALLBACK,
        )) {
            val dpu =
                WogeQuarkusHandlers(nativeDpuMode = policy, deferredPolicy = DeferredRegionPolicy(maxRegions = 2)).page(
                    application.initialDocuments.pages,
                    QuarkusPageInput { requireNotNull(it.pathParam("scenario")) },
                )
            router.get("/woge-tck/dpu/$mode/:scenario").handler(dpu)
            router.head("/woge-tck/dpu/$mode/:scenario").handler(dpu)
        }
        router.get("/woge-tck/dpu/complete").handler(
            WogeQuarkusHandlers().page(
                PageUseCase {
                    htmlPage {
                        element("ul") {
                            listOf("Alpha", "Beta", "Gamma").forEach { element("li") { text(it) } }
                        }
                    }
                },
                QuarkusPageInput { Unit },
            ),
        )
    }
}

private fun String.vertxPath(): String = replace(Regex("""\{([a-zA-Z][a-zA-Z0-9_]*)}"""), ":$1")

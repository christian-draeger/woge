package dev.woge.spring.mvc

import dev.woge.host.PageUseCase
import dev.woge.tck.AdapterTckApplication
import dev.woge.tck.AdapterTckCapability
import dev.woge.tck.AdapterTckDeferredScenario
import dev.woge.tck.AdapterTckHarnessFactory
import dev.woge.tck.AdapterTckPageScenario
import dev.woge.tck.AdapterTckRoutes
import dev.woge.tck.AdapterTckServer
import dev.woge.tck.ServerAdapterContract
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringApplication
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.web.server.context.ConfigurableWebServerApplicationContext
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class WogeSpringMvcAdapterTckTest {
    @Test
    fun `Spring MVC passes the shared server adapter contract`() {
        ServerAdapterContract(SpringMvcTckHarnessFactory).verify()
    }

    @Test
    fun `Servlet timeout cancels the page coroutine and returns a safe failure`() =
        runBlocking {
            val cancellation = CompletableDeferred<Unit>()
            val context =
                SpringApplication(SpringMvcTimeoutConfiguration::class.java)
                    .apply {
                        setDefaultProperties(testServerProperties())
                        addInitializers(
                            ApplicationContextInitializer<ConfigurableApplicationContext> { initialized ->
                                initialized.beanFactory.registerSingleton("timeoutCancellation", cancellation)
                            },
                        )
                    }.run() as ConfigurableWebServerApplicationContext

            context.use {
                val origin = "http://127.0.0.1:${requireNotNull(context.webServer).port}"
                val response =
                    HttpClient.newHttpClient().send(
                        HttpRequest.newBuilder(URI.create("$origin/timeout")).GET().build(),
                        HttpResponse.BodyHandlers.ofString(),
                    )
                assertEquals(500, response.statusCode())
                withTimeout(5.seconds) { cancellation.await() }
            }
        }
}

private object SpringMvcTckHarnessFactory : AdapterTckHarnessFactory {
    override val adapterName: String = "spring-mvc"

    override fun start(application: AdapterTckApplication): AdapterTckServer {
        val context =
            SpringApplication(SpringMvcTckConfiguration::class.java)
                .apply {
                    setDefaultProperties(testServerProperties())
                    addInitializers(
                        ApplicationContextInitializer<ConfigurableApplicationContext> { initialized ->
                            initialized.beanFactory.registerSingleton("adapterTckApplication", application)
                        },
                    )
                }.run() as ConfigurableWebServerApplicationContext
        return SpringMvcTckServer(context)
    }
}

@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
private class SpringMvcTimeoutConfiguration {
    @Bean
    fun timeoutRoute(cancellation: CompletableDeferred<Unit>): SimpleUrlHandlerMapping {
        val page =
            PageUseCase<Unit> {
                try {
                    awaitCancellation()
                } finally {
                    cancellation.complete(Unit)
                }
            }
        val handler =
            WogeSpringMvcHandlers(asyncTimeout = 100.milliseconds)
                .page(page, SpringMvcPageInput { })
        return SimpleUrlHandlerMapping(mapOf("/timeout" to handler), 0)
    }
}

@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
private class SpringMvcTckConfiguration {
    @Bean
    fun wogeSpringMvcHandlers(): WogeSpringMvcHandlers = WogeSpringMvcHandlers()

    @Bean
    fun adapterTckRoutes(
        application: AdapterTckApplication,
        handlers: WogeSpringMvcHandlers,
    ): SimpleUrlHandlerMapping {
        val page =
            handlers.page(
                application.pages,
                SpringMvcPageInput { request ->
                    AdapterTckPageScenario.fromPath(request.pathVariable("scenario"))
                },
            )
        val deferred =
            handlers.deferred(
                application.deferredRegions,
                SpringMvcPageInput { request ->
                    AdapterTckDeferredScenario.fromPath(request.pathVariable("scenario"))
                },
            )
        return SimpleUrlHandlerMapping(
            mapOf(
                AdapterTckRoutes.PAGE_PATTERN to page,
                AdapterTckRoutes.DEFERRED_PATTERN to deferred,
            ),
            0,
        )
    }
}

private class SpringMvcTckServer(
    private val context: ConfigurableWebServerApplicationContext,
) : AdapterTckServer {
    override val origin: URI = URI.create("http://127.0.0.1:${requireNotNull(context.webServer).port}")

    // Servlet exposes disconnects only through a subsequent failed write, so a passive abort is not deterministic.
    override val capabilities: Set<AdapterTckCapability> = emptySet()

    override fun close() {
        context.close()
    }
}

private fun testServerProperties(): Map<String, String> =
    mapOf(
        "server.address" to "127.0.0.1",
        "server.port" to "0",
        "spring.main.banner-mode" to "off",
        "logging.level.root" to "WARN",
    )

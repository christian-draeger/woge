package dev.woge.spring.boot.autoconfigure

import dev.woge.host.DeferredRegionsUseCase
import dev.woge.host.PageUseCase
import dev.woge.spring.mvc.SpringMvcRequestContextFactory
import dev.woge.spring.mvc.WogeSpringMvcHandlers
import dev.woge.spring.webflux.WebFluxRequestContextFactory
import dev.woge.spring.webflux.WogeWebFluxHandlers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

class WogeAutoConfigurationTest {
    @Test
    fun `activates WebFlux and discovers portable use cases`() {
        webFluxRunner()
            .withUserConfiguration(UseCaseConfiguration::class.java)
            .run { context ->
                assertNull(context.startupFailure)
                assertNotNull(context.getBean(WogeWebFluxHandlers::class.java))

                val catalog = context.getBean(WogeApplicationCatalog::class.java)
                assertEquals(listOf("homePage"), catalog.pageUseCases)
                assertEquals(listOf("homeRegions"), catalog.deferredRegionUseCases)

                val info = context.getBean(WogeRuntimeInfo::class.java)
                assertEquals(WogeSpringAdapter.WEBFLUX, info.adapter)
                assertEquals(1, info.protocolVersion)
                assertEquals(1, info.pageUseCases)
                assertEquals(1, info.deferredRegionUseCases)
            }
    }

    @Test
    fun `activates Spring MVC with the same portable use cases`() {
        mvcRunner()
            .withUserConfiguration(UseCaseConfiguration::class.java)
            .run { context ->
                assertNull(context.startupFailure)
                assertNotNull(context.getBean(WogeSpringMvcHandlers::class.java))
                assertNotNull(context.getBean(SpringMvcRequestContextFactory::class.java))

                val catalog = context.getBean(WogeApplicationCatalog::class.java)
                assertEquals(listOf("homePage"), catalog.pageUseCases)
                assertEquals(listOf("homeRegions"), catalog.deferredRegionUseCases)
                assertEquals(WogeSpringAdapter.MVC, context.getBean(WogeRuntimeInfo::class.java).adapter)
            }
    }

    @Test
    fun `backs off for an application request context factory`() {
        webFluxRunner()
            .withUserConfiguration(CustomContextConfiguration::class.java)
            .run { context ->
                assertNull(context.startupFailure)
                assertSame(
                    CustomContextConfiguration.contexts,
                    context.getBean(WebFluxRequestContextFactory::class.java),
                )
            }
    }

    @Test
    fun `MVC backs off for application context and handler factories`() {
        mvcRunner()
            .withUserConfiguration(CustomMvcConfiguration::class.java)
            .run { context ->
                assertNull(context.startupFailure)
                assertSame(
                    CustomMvcConfiguration.contexts,
                    context.getBean(SpringMvcRequestContextFactory::class.java),
                )
                assertSame(
                    CustomMvcConfiguration.handlers,
                    context.getBean(WogeSpringMvcHandlers::class.java),
                )
            }
    }

    @Test
    fun `backs off for an application handler factory`() {
        webFluxRunner()
            .withUserConfiguration(CustomHandlersConfiguration::class.java)
            .run { context ->
                assertNull(context.startupFailure)
                assertSame(
                    CustomHandlersConfiguration.handlers,
                    context.getBean(WogeWebFluxHandlers::class.java),
                )
                assertEquals(1, context.getBeansOfType(WogeWebFluxHandlers::class.java).size)
            }
    }

    @Test
    fun `binds the bounded deferred policy`() {
        webFluxRunner()
            .withPropertyValues(
                "woge.deferred.max-concurrency=3",
                "woge.deferred.region-timeout=750ms",
                "woge.mvc.async-timeout=2s",
            ).run { context ->
                assertNull(context.startupFailure)
                val properties = context.getBean(WogeProperties::class.java)
                assertEquals(3, properties.deferred.maxConcurrency)
                assertEquals(Duration.ofMillis(750), properties.deferred.regionTimeout)
                assertEquals(Duration.ofSeconds(2), properties.mvc.asyncTimeout)
            }
    }

    @Test
    fun `fails with an actionable error when both Spring web stacks are present`() {
        ReactiveWebApplicationContextRunner()
            .withConfiguration(autoConfiguration)
            .run { context ->
                val failure = context.startupFailure
                assertNotNull(failure)
                assertTrue(failure.messages().contains("both Spring MVC and WebFlux"))
                assertTrue(failure.messages().contains("woge.adapter=webflux"))
            }
    }

    @Test
    fun `explicit WebFlux selection resolves a dual-stack classpath`() {
        ReactiveWebApplicationContextRunner()
            .withConfiguration(autoConfiguration)
            .withPropertyValues("woge.adapter=webflux")
            .run { context ->
                assertNull(context.startupFailure)
                assertEquals(
                    WogeSpringAdapter.WEBFLUX,
                    context.getBean(WogeRuntimeInfo::class.java).adapter,
                )
            }
    }

    @Test
    fun `explicit MVC selection resolves a dual-stack classpath`() {
        WebApplicationContextRunner()
            .withConfiguration(autoConfiguration)
            .withPropertyValues("woge.adapter=mvc")
            .run { context ->
                assertNull(context.startupFailure)
                assertEquals(
                    WogeSpringAdapter.MVC,
                    context.getBean(WogeRuntimeInfo::class.java).adapter,
                )
            }
    }

    @Test
    fun `fails when explicit adapter and Boot application type disagree`() {
        WebApplicationContextRunner()
            .withConfiguration(autoConfiguration)
            .withPropertyValues("woge.adapter=webflux")
            .run { context ->
                val failure = context.startupFailure
                assertNotNull(failure)
                assertTrue(failure.messages().contains("Spring Boot created a 'MVC' web application"))
                assertTrue(failure.messages().contains("spring.main.web-application-type"))
            }
    }

    @Test
    fun `fails when the selected Woge adapter artifact is missing`() {
        ReactiveWebApplicationContextRunner()
            .withConfiguration(autoConfiguration)
            .withClassLoader(
                FilteredClassLoader(
                    "org.springframework.web.servlet",
                    "dev.woge.spring.webflux",
                ),
            ).run { context ->
                val failure = context.startupFailure
                assertNotNull(failure)
                assertTrue(failure.messages().contains("woge-spring-webflux"))
                assertTrue(failure.messages().contains("runtime classpath"))
            }
    }

    @Test
    fun `backs off outside a web application`() {
        ApplicationContextRunner()
            .withConfiguration(autoConfiguration)
            .run { context ->
                assertNull(context.startupFailure)
                assertTrue(context.getBeansOfType(WogeRuntimeInfo::class.java).isEmpty())
            }
    }

    @Test
    fun `publishes IDE configuration metadata`() {
        val metadata =
            requireNotNull(javaClass.classLoader.getResource("META-INF/spring-configuration-metadata.json"))
                .readText()
        assertTrue(metadata.contains("woge.adapter"))
        assertTrue(metadata.contains("woge.deferred.max-concurrency"))
        assertTrue(metadata.contains("woge.deferred.region-timeout"))
        assertTrue(metadata.contains("woge.mvc.async-timeout"))
    }

    private fun webFluxRunner(): ReactiveWebApplicationContextRunner =
        ReactiveWebApplicationContextRunner()
            .withConfiguration(autoConfiguration)
            .withClassLoader(FilteredClassLoader("org.springframework.web.servlet"))

    private fun mvcRunner(): WebApplicationContextRunner =
        WebApplicationContextRunner()
            .withConfiguration(autoConfiguration)
            .withClassLoader(FilteredClassLoader("org.springframework.web.reactive"))

    private fun Throwable?.messages(): String =
        generateSequence(this) {
            it.cause
        }.joinToString("\n") { it.message.orEmpty() }

    private companion object {
        private val autoConfiguration = AutoConfigurations.of(WogeAutoConfiguration::class.java)
    }

    @Configuration(proxyBeanMethods = false)
    internal class UseCaseConfiguration {
        @Bean
        fun homePage(): PageUseCase<Unit> = PageUseCase { error("not executed during discovery") }

        @Bean
        fun homeRegions(): DeferredRegionsUseCase<Unit> = DeferredRegionsUseCase { emptyList() }
    }

    @Configuration(proxyBeanMethods = false)
    internal class CustomContextConfiguration {
        @Bean
        fun customWebFluxRequestContextFactory(): WebFluxRequestContextFactory = contexts

        companion object {
            val contexts = WebFluxRequestContextFactory { error("not executed during startup") }
        }
    }

    @Configuration(proxyBeanMethods = false)
    internal class CustomHandlersConfiguration {
        @Bean
        fun customWogeWebFluxHandlers(): WogeWebFluxHandlers = handlers

        companion object {
            val handlers = WogeWebFluxHandlers()
        }
    }

    @Configuration(proxyBeanMethods = false)
    internal class CustomMvcConfiguration {
        @Bean
        fun customSpringMvcRequestContextFactory(): SpringMvcRequestContextFactory = contexts

        @Bean
        fun customWogeSpringMvcHandlers(): WogeSpringMvcHandlers = handlers

        companion object {
            val contexts = SpringMvcRequestContextFactory { error("not executed during startup") }
            val handlers = WogeSpringMvcHandlers(contexts)
        }
    }
}

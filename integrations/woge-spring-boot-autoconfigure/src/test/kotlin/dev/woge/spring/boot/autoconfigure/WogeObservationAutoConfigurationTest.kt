package dev.woge.spring.boot.autoconfigure

import dev.woge.host.WogeObservationContext
import dev.woge.host.WogeObservationId
import dev.woge.host.WogeObserver
import dev.woge.host.WogeOperation
import dev.woge.host.WogeOperationFinished
import dev.woge.host.WogeOperationStarted
import dev.woge.host.WogeOutcome
import dev.woge.protocol.PageEpoch
import dev.woge.protocol.PatchTarget
import dev.woge.protocol.RegionTargetId
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationHandler
import io.micrometer.observation.ObservationRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner
import kotlin.time.Duration.Companion.milliseconds

class WogeObservationAutoConfigurationTest {
    @Test
    fun `registry activates bridge and application observer overrides it`() {
        val registry = ObservationRegistry.create()
        runner().withBean(ObservationRegistry::class.java, { registry }).run { context ->
            assertNull(context.startupFailure)
            assertEquals(MicrometerWogeObserver::class.java, context.getBean(WogeObserver::class.java).javaClass)
        }
        val custom = WogeObserver { }
        runner()
            .withBean(ObservationRegistry::class.java, { registry })
            .withBean(WogeObserver::class.java, { custom })
            .run { context -> assertSame(custom, context.getBean(WogeObserver::class.java)) }
    }

    @Test
    fun `no registry or no Micrometer retains neutral fallback`() {
        runner().run { context -> assertSame(WogeObserver.NONE, context.getBean(WogeObserver::class.java)) }
        runner()
            .withClassLoader(FilteredClassLoader("io.micrometer", "org.springframework.web.servlet"))
            .run { context ->
                assertNull(context.startupFailure)
                assertSame(WogeObserver.NONE, context.getBean(WogeObserver::class.java))
            }
    }

    @Test
    fun `overlapping operations pair by identity without scopes or high cardinality metric labels`() {
        val recorder = RecordingHandler()
        val registry = ObservationRegistry.create()
        registry.observationConfig().observationHandler(recorder)
        val observer = MicrometerWogeObserver(registry)
        val first = WogeObservationId.of(1)
        val second = WogeObservationId.of(2)
        val correlation =
            WogeObservationContext(target = PatchTarget(PageEpoch.of("page"), RegionTargetId.of("region")))
        observer.onEvent(WogeOperationStarted(first, WogeOperation.PAGE_REQUEST, correlation))
        observer.onEvent(WogeOperationStarted(second, WogeOperation.DEFERRED_REGION))
        observer.onEvent(
            WogeOperationFinished(second, WogeOperation.DEFERRED_REGION, WogeOutcome.CANCELLED, 1.milliseconds),
        )
        observer.onEvent(
            WogeOperationFinished(first, WogeOperation.PAGE_REQUEST, WogeOutcome.SUCCEEDED, 2.milliseconds),
        )
        observer.onEvent(
            WogeOperationFinished(first, WogeOperation.PAGE_REQUEST, WogeOutcome.SUCCEEDED, 2.milliseconds),
        )
        assertNull(registry.currentObservation)
        assertEquals(2, recorder.stopped.size)
        assertEquals(
            "cancelled",
            recorder.stopped
                .first()
                .getLowCardinalityKeyValue("outcome")
                ?.value,
        )
        val page = recorder.stopped.last()
        assertEquals("woge.operation", page.name)
        assertEquals(setOf("operation", "outcome"), page.lowCardinalityKeyValues.map { it.key }.toSet())
        assertEquals("region", page.getHighCardinalityKeyValue("woge.region")?.value)
    }

    @Test
    fun `observation handler failure cannot escape the semantic port`() {
        val registry = ObservationRegistry.create()
        registry.observationConfig().observationHandler(
            object : ObservationHandler<Observation.Context> {
                override fun supportsContext(context: Observation.Context): Boolean = true

                override fun onStart(context: Observation.Context) {
                    error("exporter failed")
                }
            },
        )
        MicrometerWogeObserver(
            registry,
        ).onEvent(WogeOperationStarted(WogeObservationId.of(1), WogeOperation.PAGE_REQUEST))
    }

    private fun runner(): ReactiveWebApplicationContextRunner =
        ReactiveWebApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(WogeAutoConfiguration::class.java, WogeObservationAutoConfiguration::class.java),
            ).withClassLoader(FilteredClassLoader("org.springframework.web.servlet"))

    private class RecordingHandler : ObservationHandler<Observation.Context> {
        val stopped = mutableListOf<Observation.Context>()

        override fun supportsContext(context: Observation.Context): Boolean = true

        override fun onStop(context: Observation.Context) {
            stopped.add(context)
        }
    }
}

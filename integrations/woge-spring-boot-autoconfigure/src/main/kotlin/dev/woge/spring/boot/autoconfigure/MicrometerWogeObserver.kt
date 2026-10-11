package dev.woge.spring.boot.autoconfigure

import dev.woge.host.WogeObservationEvent
import dev.woge.host.WogeObservationId
import dev.woge.host.WogeObserver
import dev.woge.host.WogeOperationFinished
import dev.woge.host.WogeOperationStarted
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import java.util.concurrent.ConcurrentHashMap

internal class MicrometerWogeObserver(
    private val registry: ObservationRegistry,
) : WogeObserver {
    private val active = ConcurrentHashMap<WogeObservationId, Observation>()

    override fun onEvent(event: WogeObservationEvent) {
        runCatching {
            when (event) {
                is WogeOperationStarted -> start(event)
                is WogeOperationFinished -> finish(event)
            }
        }
    }

    private fun start(event: WogeOperationStarted) {
        // Do not open thread-local scopes: concurrent regions may finish on another coroutine thread.
        val observation =
            Observation
                .createNotStarted("woge.operation", registry)
                .contextualName("woge.${event.operation.semanticName}")
                .lowCardinalityKeyValue("operation", event.operation.semanticName)
        event.context.requestTrace?.let { trace ->
            observation.highCardinalityKeyValue("woge.request.id", trace.requestId.value)
            observation.highCardinalityKeyValue("woge.correlation.id", trace.correlationId.value)
        }
        event.context.target?.let { target ->
            observation.highCardinalityKeyValue("woge.page.epoch", target.pageEpoch.value)
            observation.highCardinalityKeyValue("woge.region", target.region.value)
        }
        event.context.patchId?.let { observation.highCardinalityKeyValue("woge.patch.id", it.value) }
        active.computeIfAbsent(event.observationId) { observation.start() }
    }

    private fun finish(event: WogeOperationFinished) {
        val observation = active.remove(event.observationId) ?: return
        observation.lowCardinalityKeyValue("outcome", event.outcome.semanticName)
        observation.stop()
    }
}

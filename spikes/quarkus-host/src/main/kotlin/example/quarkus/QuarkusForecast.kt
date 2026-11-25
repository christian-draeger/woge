package example.quarkus

import dev.woge.examples.gradient.t06deferredregion.ForecastPage
import dev.woge.examples.gradient.t06deferredregion.ForecastPatchesRoute
import dev.woge.examples.gradient.t06deferredregion.ForecastRoute
import dev.woge.host.PageRequest
import dev.woge.host.PageRoute
import dev.woge.host.RouteParameters
import dev.woge.host.RouteValueException
import dev.woge.host.requestsUnsupportedActionPatchVersion
import dev.woge.protocol.PageEpoch
import dev.woge.protocol.PatchId
import dev.woge.protocol.PatchStreamV1
import dev.woge.runtime.DeferredRegionExecutor
import dev.woge.runtime.DeferredRegionLimitException
import dev.woge.runtime.DeferredRegionPolicy
import dev.woge.runtime.encodeDeferredPatchStream
import io.vertx.core.buffer.Buffer
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.coroutines.coAwait
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

@ApplicationScoped
class QuarkusForecast {
    private val forecast = ForecastPage()
    private val executor = DeferredRegionExecutor(DeferredRegionPolicy())

    fun register(@Observes router: Router) {
        router.get(ForecastRoute.path).handler { call ->
            val input = call.decode(ForecastRoute)
            if (input != null) serve(call, forecast, input)
        }
        router.get("/forecast/woge-patches/:epoch").handler(::patches)
    }

    private fun patches(call: RoutingContext) {
        val input = call.decode(ForecastPatchesRoute) ?: return
        if (runCatching { PageEpoch.of(input.epoch) }.isFailure) {
            call.response().setStatusCode(404).end()
            return
        }
        val accept = call.request().headers().getAll("Accept").joinToString(",")
        if (requestsUnsupportedActionPatchVersion(accept)) {
            call.response().setStatusCode(406).putHeader("Woge-Protocol-Error", "unsupported-version").end()
            return
        }

        exchange(call) { response, context ->
            val regions =
                try {
                    withContext(Dispatchers.Default) {
                        executor.prepare(forecast.regions(PageRequest(input, context)), context.trace)
                    }
                } catch (_: DeferredRegionLimitException) {
                    response.setStatusCode(503).end().coAwait()
                    return@exchange
                }

            var number = 0
            response
                .putHeader("Content-Type", PatchStreamV1.MEDIA_TYPE)
                .putHeader("Cache-Control", "no-store, no-transform")
                .putHeader("X-Accel-Buffering", "no")
                .setChunked(true)
            executor
                .execute(regions, context.trace)
                .encodeDeferredPatchStream(requestTrace = context.trace) {
                    number += 1
                    PatchId.of("forecast-$number")
                }.flowOn(Dispatchers.Default)
                .buffer(0)
                .collect { chunk -> response.write(Buffer.buffer(chunk.bytes)).coAwait() }
            response.end().coAwait()
        }
    }
}

private fun <Input : Any> RoutingContext.decode(route: PageRoute<Input>): Input? =
    try {
        route.decode(object : RouteParameters {
            override fun path(name: String): String? = pathParam(name)

            override fun query(name: String): String? = queryParams()[name]
        })
    } catch (invalid: RouteValueException) {
        response().setStatusCode(invalid.category.status.code).end()
        null
    }

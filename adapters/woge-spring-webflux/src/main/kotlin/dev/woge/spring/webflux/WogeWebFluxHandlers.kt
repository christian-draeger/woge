package dev.woge.spring.webflux

import dev.woge.host.DeferredRegionsUseCase
import dev.woge.host.PageUseCase
import dev.woge.runtime.DeferredRegionPolicy
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Creates route-local Woge handlers with one shared WebFlux runtime policy. */
public class WogeWebFluxHandlers(
    private val contexts: WebFluxRequestContextFactory = DefaultWebFluxRequestContextFactory,
    private val maxConcurrency: Int = DeferredRegionPolicy.DEFAULT_MAX_CONCURRENCY,
    private val regionTimeout: Duration = 30.seconds,
) {
    init {
        DeferredRegionPolicy(maxConcurrency, regionTimeout)
    }

    /** Creates a handler for one typed page and its route-local input decoder. */
    public fun <Input : Any> page(
        useCase: PageUseCase<Input>,
        input: WebFluxPageInput<Input>,
    ): WogeWebFluxPageHandler<Input> = WogeWebFluxPageHandler(useCase, input, contexts)

    /** Creates a handler for one typed deferred-region stream and its route-local input decoder. */
    public fun <Input : Any> deferred(
        useCase: DeferredRegionsUseCase<Input>,
        input: WebFluxPageInput<Input>,
    ): WogeWebFluxDeferredHandler<Input> =
        WogeWebFluxDeferredHandler(
            regions = useCase,
            input = input,
            contexts = contexts,
            maxConcurrency = maxConcurrency,
            regionTimeout = regionTimeout,
        )
}

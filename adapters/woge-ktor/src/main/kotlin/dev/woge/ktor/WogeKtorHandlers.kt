package dev.woge.ktor

import dev.woge.host.DeferredRegionsUseCase
import dev.woge.host.PageUseCase
import dev.woge.runtime.DeferredRegionPolicy
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Creates route-local Woge handlers with one shared Ktor runtime policy. */
public class WogeKtorHandlers(
    private val contexts: KtorRequestContextFactory = DefaultKtorRequestContextFactory,
    private val maxConcurrency: Int = DeferredRegionPolicy.DEFAULT_MAX_CONCURRENCY,
    private val regionTimeout: Duration = 30.seconds,
) {
    init {
        DeferredRegionPolicy(maxConcurrency, regionTimeout)
    }

    /** Creates a handler for one typed page and its route-local input decoder. */
    public fun <Input : Any> page(
        useCase: PageUseCase<Input>,
        input: KtorPageInput<Input>,
    ): WogeKtorPageHandler<Input> = WogeKtorPageHandler(useCase, input, contexts)

    /** Creates a handler for one typed deferred-region stream and its route-local input decoder. */
    public fun <Input : Any> deferred(
        useCase: DeferredRegionsUseCase<Input>,
        input: KtorPageInput<Input>,
    ): WogeKtorDeferredHandler<Input> =
        WogeKtorDeferredHandler(
            regions = useCase,
            input = input,
            contexts = contexts,
            maxConcurrency = maxConcurrency,
            regionTimeout = regionTimeout,
        )
}

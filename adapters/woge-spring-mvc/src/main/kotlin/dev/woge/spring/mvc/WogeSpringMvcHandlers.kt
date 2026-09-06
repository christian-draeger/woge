package dev.woge.spring.mvc

import dev.woge.host.DeferredRegionsUseCase
import dev.woge.host.PageUseCase
import dev.woge.runtime.DeferredRegionPolicy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Creates route-local Woge handlers with one shared Spring MVC execution policy. */
public class WogeSpringMvcHandlers(
    private val contexts: SpringMvcRequestContextFactory = DefaultSpringMvcRequestContextFactory,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val asyncTimeout: Duration = 60.seconds,
    private val maxConcurrency: Int = DeferredRegionPolicy.DEFAULT_MAX_CONCURRENCY,
    private val regionTimeout: Duration = 30.seconds,
) {
    private val policy = DeferredRegionPolicy(maxConcurrency, regionTimeout)
    private val asyncTimeoutMillis: Long

    init {
        require(asyncTimeout.isFinite() && asyncTimeout > Duration.ZERO) {
            "Spring MVC async timeout must be positive and finite"
        }
        asyncTimeoutMillis = asyncTimeout.inWholeMilliseconds
        require(asyncTimeoutMillis > 0) { "Spring MVC async timeout must be at least one millisecond" }
    }

    /** Creates a Servlet handler for one typed page and its route-local input decoder. */
    public fun <Input : Any> page(
        useCase: PageUseCase<Input>,
        input: SpringMvcPageInput<Input>,
    ): WogeSpringMvcPageHandler<Input> =
        WogeSpringMvcPageHandler(useCase, input, contexts, dispatcher, asyncTimeoutMillis)

    /** Creates a Servlet handler for one typed deferred-region stream and input decoder. */
    public fun <Input : Any> deferred(
        useCase: DeferredRegionsUseCase<Input>,
        input: SpringMvcPageInput<Input>,
    ): WogeSpringMvcDeferredHandler<Input> =
        WogeSpringMvcDeferredHandler(useCase, input, contexts, dispatcher, asyncTimeoutMillis, policy)
}

package dev.woge.spring.webflux

import dev.woge.host.DeferredRegionsUseCase
import dev.woge.host.FailurePages
import dev.woge.host.PageRoute
import dev.woge.host.PageUseCase
import dev.woge.host.WogeObserver
import dev.woge.runtime.DeferredRegionPolicy
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Creates route-local Woge handlers with one shared WebFlux runtime policy. */
public class WogeWebFluxHandlers(
    private val contexts: WebFluxRequestContextFactory = DefaultWebFluxRequestContextFactory,
    private val maxConcurrency: Int = DeferredRegionPolicy.DEFAULT_MAX_CONCURRENCY,
    private val regionTimeout: Duration = 30.seconds,
    private val observer: WogeObserver = WogeObserver.NONE,
    private val failurePages: FailurePages = FailurePages.NONE,
) {
    init {
        DeferredRegionPolicy(maxConcurrency, regionTimeout)
    }

    /** Creates a handler for one typed page and its route-local input decoder. */
    public fun <Input : Any> page(
        useCase: PageUseCase<Input>,
        input: WebFluxPageInput<Input>,
    ): WogeWebFluxPageHandler<Input> = WogeWebFluxPageHandler(useCase, input, contexts, observer, failurePages)

    /**
     * Creates a handler for a page with a generated route. Register it at the route's own path:
     * `GET(ProjectPageRoute.path, handlers.page(projectPage, ProjectPageRoute)::handle)`.
     */
    public fun <Input : Any> page(
        useCase: PageUseCase<Input>,
        route: PageRoute<Input>,
    ): WogeWebFluxPageHandler<Input> = page(useCase, route.webFluxInput())

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
            observer = observer,
        )
}

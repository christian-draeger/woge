package dev.woge.spring.webflux

import dev.woge.host.ActionExecutor
import dev.woge.host.DeferredRegionsUseCase
import dev.woge.host.FailurePages
import dev.woge.host.LiveLimits
import dev.woge.host.LiveUseCase
import dev.woge.host.PageRoute
import dev.woge.host.PageUseCase
import dev.woge.host.PatchStreamLimits
import dev.woge.host.WogeObserver
import dev.woge.runtime.DeferredRegionPolicy
import dev.woge.runtime.LiveAdmission
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Creates route-local Woge handlers with one shared WebFlux runtime policy. */
@Suppress("LongParameterList")
public class WogeWebFluxHandlers(
    private val contexts: WebFluxRequestContextFactory = DefaultWebFluxRequestContextFactory,
    private val maxConcurrency: Int = DeferredRegionPolicy.DEFAULT_MAX_CONCURRENCY,
    private val regionTimeout: Duration = 30.seconds,
    private val observer: WogeObserver = WogeObserver.NONE,
    private val failurePages: FailurePages = FailurePages.NONE,
    private val maxRegions: Int = DeferredRegionPolicy.DEFAULT_MAX_REGIONS,
    private val patchStreamLimits: PatchStreamLimits = PatchStreamLimits(),
    liveLimits: LiveLimits = LiveLimits(),
) {
    private val liveAdmission = LiveAdmission(liveLimits)

    init {
        DeferredRegionPolicy(maxConcurrency, regionTimeout, maxRegions)
    }

    /** Binds a POST action; the explicit context factory establishes authentication and CSRF before decoding. */
    public fun <Command : Any> action(
        executor: ActionExecutor<Command>,
        input: WebFluxPageInput<Command>,
        securityContexts: WebFluxRequestContextFactory,
    ): WogeWebFluxActionHandler<Command> =
        WogeWebFluxActionHandler(
            WogeWebFluxPageHandler(
                PageUseCase { request -> executor.execute(request) },
                input,
                securityContexts,
                observer,
                failurePages,
            ),
        )

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
            maxRegions = maxRegions,
            patchStreamLimits = patchStreamLimits,
        )

    /**
     * Creates a live-update (Server-Sent Events) handler. All live handlers from this factory share
     * one application-wide subscription limit.
     */
    public fun <Input : Any> live(
        useCase: LiveUseCase<Input>,
        input: WebFluxPageInput<Input>,
    ): WogeWebFluxLiveHandler<Input> = WogeWebFluxLiveHandler(useCase, input, contexts, liveAdmission, observer)

    /** Creates a live-update handler for a generated route. Register it at the route's own path. */
    public fun <Input : Any> live(
        useCase: LiveUseCase<Input>,
        route: PageRoute<Input>,
    ): WogeWebFluxLiveHandler<Input> = live(useCase, route.webFluxInput())
}

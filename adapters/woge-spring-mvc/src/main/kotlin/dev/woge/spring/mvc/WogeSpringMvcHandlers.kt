package dev.woge.spring.mvc

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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Creates route-local Woge handlers with one shared Spring MVC execution policy. */
@Suppress("LongParameterList")
public class WogeSpringMvcHandlers(
    private val contexts: SpringMvcRequestContextFactory = SameOriginSpringMvcRequestContextFactory,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val asyncTimeout: Duration = 60.seconds,
    private val maxConcurrency: Int = DeferredRegionPolicy.DEFAULT_MAX_CONCURRENCY,
    private val regionTimeout: Duration = 30.seconds,
    private val observer: WogeObserver = WogeObserver.NONE,
    private val failurePages: FailurePages = FailurePages.NONE,
    private val maxRegions: Int = DeferredRegionPolicy.DEFAULT_MAX_REGIONS,
    private val patchStreamLimits: PatchStreamLimits = PatchStreamLimits(),
    liveLimits: LiveLimits = LiveLimits(),
) {
    private val policy = DeferredRegionPolicy(maxConcurrency, regionTimeout, maxRegions)
    private val liveAdmission = LiveAdmission(liveLimits)
    private val asyncTimeoutMillis: Long

    init {
        require(asyncTimeout.isFinite() && asyncTimeout > Duration.ZERO) {
            "Spring MVC async timeout must be positive and finite"
        }

        asyncTimeoutMillis = asyncTimeout.inWholeMilliseconds
        require(asyncTimeoutMillis > 0) { "Spring MVC async timeout must be at least one millisecond" }
    }

    /**
     * Binds a POST action. The built-in same-origin policy is the default; provide a factory for
     * authentication or token-based CSRF.
     */
    public fun <Command : Any> action(
        executor: ActionExecutor<Command>,
        input: SpringMvcPageInput<Command>,
        securityContexts: SpringMvcRequestContextFactory = SameOriginSpringMvcRequestContextFactory,
    ): WogeSpringMvcPageHandler<Command> =
        WogeSpringMvcPageHandler(
            PageUseCase { request -> executor.execute(request) },
            input,
            securityContexts,
            dispatcher,
            asyncTimeoutMillis,
            observer,
            failurePages,
            setOf("POST"),
        )

    /** Creates a Servlet handler for one typed page and its route-local input decoder. */
    public fun <Input : Any> page(
        useCase: PageUseCase<Input>,
        input: SpringMvcPageInput<Input>,
    ): WogeSpringMvcPageHandler<Input> =
        WogeSpringMvcPageHandler(useCase, input, contexts, dispatcher, asyncTimeoutMillis, observer, failurePages)

    /**
     * Creates a handler for a page with a generated route. Map it at the route's own path:
     * `ProjectPageRoute.path to handlers.page(projectPage, ProjectPageRoute)`.
     */
    public fun <Input : Any> page(
        useCase: PageUseCase<Input>,
        route: PageRoute<Input>,
    ): WogeSpringMvcPageHandler<Input> = page(useCase, route.springMvcInput())

    /** Creates a Servlet handler for one typed deferred-region stream and input decoder. */
    public fun <Input : Any> deferred(
        useCase: DeferredRegionsUseCase<Input>,
        input: SpringMvcPageInput<Input>,
    ): WogeSpringMvcDeferredHandler<Input> =
        WogeSpringMvcDeferredHandler(
            useCase,
            input,
            contexts,
            dispatcher,
            asyncTimeoutMillis,
            policy,
            observer,
            patchStreamLimits,
        )

    /**
     * Creates a live-update (Server-Sent Events) handler. All live handlers from this factory share
     * one application-wide subscription limit.
     */
    public fun <Input : Any> live(
        useCase: LiveUseCase<Input>,
        input: SpringMvcPageInput<Input>,
    ): WogeSpringMvcLiveHandler<Input> =
        WogeSpringMvcLiveHandler(useCase, input, contexts, dispatcher, liveAdmission, observer)

    /** Creates a live-update handler for a generated route. Map it at the route's own path. */
    public fun <Input : Any> live(
        useCase: LiveUseCase<Input>,
        route: PageRoute<Input>,
    ): WogeSpringMvcLiveHandler<Input> = live(useCase, route.springMvcInput())
}

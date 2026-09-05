package dev.woge.host

/** Page-load work re-authorized when a browser opens its deferred patch stream. */
public fun interface DeferredRegionsUseCase<Input : Any> {
    public suspend fun regions(request: PageRequest<Input>): Iterable<DeferredRegion>
}

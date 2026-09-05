package dev.woge.spring.boot.autoconfigure

/** Names of portable Woge application entry points discovered as Spring beans. */
public class WogeApplicationCatalog internal constructor(
    pageUseCases: Iterable<String>,
    deferredRegionUseCases: Iterable<String>,
) {
    /** Stable, sorted Spring bean names implementing a typed Woge page use case. */
    public val pageUseCases: List<String> = pageUseCases.distinct().sorted()

    /** Stable, sorted Spring bean names implementing a typed deferred-regions use case. */
    public val deferredRegionUseCases: List<String> = deferredRegionUseCases.distinct().sorted()
}

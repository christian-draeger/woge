package dev.woge.spring.boot.autoconfigure

/** Inspectable result of Woge's deterministic Spring Boot startup selection. */
public class WogeRuntimeInfo internal constructor(
    /** Active Spring transport adapter. */
    public val adapter: WogeSpringAdapter,
    /** Active Woge patch protocol version. */
    public val protocolVersion: Int,
    /** Woge artifact version, or `development` when running from classes directories. */
    public val runtimeVersion: String,
    /** Number of typed page use-case beans discovered at startup. */
    public val pageUseCases: Int,
    /** Number of deferred-region use-case beans discovered at startup. */
    public val deferredRegionUseCases: Int,
)

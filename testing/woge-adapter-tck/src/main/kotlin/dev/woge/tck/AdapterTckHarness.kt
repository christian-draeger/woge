package dev.woge.tck

import java.net.URI

/** Creates one real HTTP server around the framework-neutral TCK application. */
public interface AdapterTckHarnessFactory {
    /** Stable human-readable adapter name included in every contract failure. */
    public val adapterName: String

    /** Starts the adapter on an ephemeral local port and returns its HTTP boundary. */
    public fun start(application: AdapterTckApplication): AdapterTckServer
}

/** A running adapter owned by one contract invocation. */
public interface AdapterTckServer : AutoCloseable {
    /** Absolute HTTP origin without an application path. */
    public val origin: URI

    /** Optional lifecycle behavior that this harness can expose deterministically. */
    public val capabilities: Set<AdapterTckCapability>

    override fun close()
}

/** Host lifecycle behavior that needs an explicit real-transport test hook. */
public enum class AdapterTckCapability {
    /** Closing a committed response cancels its outstanding structured children. */
    CLIENT_ABORT_CANCELLATION,
}

/** Additive capability suite for actions, caching, multipart, SSE and later host contracts. */
public interface AdapterTckExtension {
    /** Stable diagnostic name for this extension suite. */
    public val name: String

    /** Verifies extension-specific routes exposed by the adapter harness. */
    public suspend fun verify(server: AdapterTckServer)
}

/** Canonical paths that every server-adapter harness binds. */
public object AdapterTckRoutes {
    public const val PAGE_PATTERN: String = "/woge-tck/pages/{scenario}"
    public const val DEFERRED_PATTERN: String = "/woge-tck/deferred/{scenario}"

    public fun page(scenario: AdapterTckPageScenario): String = "/woge-tck/pages/${scenario.pathSegment}"

    public fun deferred(scenario: AdapterTckDeferredScenario): String = "/woge-tck/deferred/${scenario.pathSegment}"
}

/** Identifies whether a failure belongs to TCK code, its fixture or the adapter under test. */
public enum class AdapterTckFailureOwner {
    CONTRACT,
    FIXTURE,
    ADAPTER,
}

/** One source-located contract failure with stable ownership and contract identifiers. */
public class AdapterTckViolation internal constructor(
    public val owner: AdapterTckFailureOwner,
    public val adapterName: String,
    public val contract: String,
    detail: String,
    cause: Throwable? = null,
) : AssertionError("[WOGE-TCK][adapter=$adapterName][owner=${owner.name.lowercase()}][contract=$contract] $detail") {
    init {
        cause?.let(::initCause)
    }
}

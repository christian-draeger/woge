package dev.woge.runtime

/** Experimental selection policy for the initial-document native DPU encoder. */
internal enum class NativeDpuMode {
    /** Keep all responses on the supported framed fallback path. */
    OFF,

    /** Use native DPU only when the client has advertised support and content is eligible. */
    AUTO,

    /** Test-only override that exercises native output independent of the capability cookie. */
    FORCE_NATIVE,

    /** Test-only override that always exercises the framed fallback path. */
    FORCE_FALLBACK,
}

package dev.woge.development

import java.net.URI

/** Monotonic identity of one requested development build. */
@ExperimentalWogeDevelopmentApi
@JvmInline
public value class BuildId private constructor(
    public val value: Long,
) : Comparable<BuildId> {
    override fun compareTo(other: BuildId): Int = value.compareTo(other.value)

    public companion object {
        public val FIRST: BuildId = BuildId(1)

        public fun of(value: Long): BuildId {
            require(value > 0) { "Build ID must be positive" }
            return BuildId(value)
        }

        public fun after(previous: BuildId): BuildId {
            require(previous.value < Long.MAX_VALUE) { "Build ID overflow requires a new development session" }
            return BuildId(previous.value + 1)
        }
    }
}

/** Monotonic identity of one ready server process within a development session. */
@ExperimentalWogeDevelopmentApi
@JvmInline
public value class ServerGeneration private constructor(
    public val value: Long,
) : Comparable<ServerGeneration> {
    override fun compareTo(other: ServerGeneration): Int = value.compareTo(other.value)

    public companion object {
        public val FIRST: ServerGeneration = ServerGeneration(1)

        public fun of(value: Long): ServerGeneration {
            require(value > 0) { "Server generation must be positive" }
            return ServerGeneration(value)
        }

        public fun after(previous: ServerGeneration): ServerGeneration {
            require(previous.value < Long.MAX_VALUE) {
                "Server generation overflow requires a new development session"
            }
            return ServerGeneration(previous.value + 1)
        }
    }
}

/** A repository-relative path safe to expose in development diagnostics. */
@ExperimentalWogeDevelopmentApi
@JvmInline
public value class DevelopmentSourcePath private constructor(
    public val value: String,
) {
    public companion object {
        public fun of(value: String): DevelopmentSourcePath {
            require(value.isNotBlank() && value.length <= MAX_SOURCE_PATH_LENGTH) {
                "Development source path must contain 1 to $MAX_SOURCE_PATH_LENGTH characters"
            }
            require(!value.startsWith('/') && !WINDOWS_ABSOLUTE_PATH.matches(value)) {
                "Development source path must be repository-relative"
            }
            require(value.split('/', '\\').none { it == ".." }) {
                "Development source path must not traverse parent directories"
            }
            require(value.none(Char::isISOControl)) { "Development source path must not contain control characters" }
            return DevelopmentSourcePath(value)
        }
    }
}

/** A loopback HTTP URL exposed by a local development session. */
@ExperimentalWogeDevelopmentApi
@JvmInline
public value class DevelopmentUrl private constructor(
    public val value: String,
) {
    public companion object {
        public fun local(value: String): DevelopmentUrl {
            val uri =
                runCatching { URI(value) }
                    .getOrElse { throw IllegalArgumentException("Invalid development URL", it) }
            require(uri.isAbsolute && uri.scheme.lowercase() in HTTP_SCHEMES) {
                "Development URL must use HTTP or HTTPS"
            }
            require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
                "Development URL must not contain user information, a query or a fragment"
            }
            val host = requireNotNull(uri.host) { "Development URL must contain a host" }.lowercase()
            require(host == "localhost" || host.endsWith(".localhost") || host in LOOPBACK_ADDRESSES) {
                "Development URL must use a loopback host"
            }
            return DevelopmentUrl(uri.toASCIIString())
        }
    }
}

private const val MAX_SOURCE_PATH_LENGTH: Int = 512
private val WINDOWS_ABSOLUTE_PATH: Regex = Regex("^[A-Za-z]:[\\\\/].*")
private val HTTP_SCHEMES: Set<String> = setOf("http", "https")
private val LOOPBACK_ADDRESSES: Set<String> =
    setOf(
        "127.0.0.1",
        "::1",
        "[::1]",
        "0:0:0:0:0:0:0:1",
        "[0:0:0:0:0:0:0:1]",
    )

package dev.woge.host

import java.util.Collections

/** Opaque principal subject supplied by the configured host authentication integration. */
@JvmInline
public value class PrincipalId private constructor(
    public val value: String,
) {
    public companion object {
        public fun of(value: String): PrincipalId {
            require(value.isNotBlank()) { "Principal ID must not be blank" }
            require(value.length <= MAX_SECURITY_FACT_LENGTH) { "Principal ID is too long" }
            require(value.none { it.code < ASCII_SPACE || it.code == DELETE }) {
                "Principal ID must not contain control characters"
            }
            return PrincipalId(value)
        }
    }
}

/** One adapter-supplied role, scope or capability fact. It is not a domain authorization decision. */
@JvmInline
public value class Capability private constructor(
    public val value: String,
) {
    public companion object {
        public fun of(value: String): Capability {
            require(value.length in 1..MAX_SECURITY_FACT_LENGTH) { "Capability must not be empty or too long" }
            require(value.all(::isCapabilityCharacter)) {
                "Capability must contain only ASCII letters, digits, '.', '_', ':', or '-'"
            }
            return Capability(value)
        }
    }
}

/** Authenticated identity facts copied out of a host framework's security context. */
public class PrincipalFacts(
    public val subject: PrincipalId,
    capabilities: Iterable<Capability> = emptyList(),
) {
    public val capabilities: Set<Capability> =
        Collections.unmodifiableSet(LinkedHashSet(capabilities.toList()))

    public fun has(capability: Capability): Boolean = capability in capabilities

    override fun toString(): String = "PrincipalFacts(subject=<redacted>, capabilities=${capabilities.size})"
}

/** Authentication facts at adapter ingress. Domain authorization remains application-owned. */
public sealed interface AuthenticationFacts {
    public val kind: Kind

    public data object Anonymous : AuthenticationFacts {
        override val kind: Kind = Kind.ANONYMOUS
    }

    public class Authenticated(
        public val principal: PrincipalFacts,
    ) : AuthenticationFacts {
        override val kind: Kind = Kind.AUTHENTICATED

        override fun toString(): String = "AuthenticationFacts.Authenticated(principal=$principal)"
    }

    public enum class Kind {
        ANONYMOUS,
        AUTHENTICATED,
    }
}

/**
 * Result of the adapter's CSRF policy before portable code is invoked.
 *
 * Missing or invalid verification is intentionally absent: an adapter must reject that request
 * before calling Woge. NOT_REQUIRED is valid only when the adapter's method policy says so.
 */
public enum class CsrfVerification {
    NOT_REQUIRED,
    VERIFIED,
}

/** Authentication and CSRF facts established by the host adapter before portable execution. */
public data class RequestSecurity(
    public val authentication: AuthenticationFacts = AuthenticationFacts.Anonymous,
    public val csrf: CsrfVerification = CsrfVerification.NOT_REQUIRED,
)

private fun isCapabilityCharacter(character: Char): Boolean =
    character in 'a'..'z' ||
        character in 'A'..'Z' ||
        character in '0'..'9' ||
        character == '.' ||
        character == '_' ||
        character == ':' ||
        character == '-'

private const val ASCII_SPACE: Int = 0x20
private const val DELETE: Int = 0x7f
private const val MAX_SECURITY_FACT_LENGTH: Int = 256

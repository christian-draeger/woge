package dev.woge.host

import java.util.UUID

public const val MUTATION_REQUEST_IDENTITY_HEADER: String = "Woge-Request-Identity"

/** Client-generated mutation identity. It is neither an authentication token nor a domain version. */
public class MutationRequestIdentity private constructor(
    public val value: UUID,
) {
    override fun equals(other: Any?): Boolean = other is MutationRequestIdentity && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "MutationRequestIdentity(<redacted>)"

    public companion object {
        public fun of(value: UUID): MutationRequestIdentity = MutationRequestIdentity(value)
    }
}

/** Invalid or repeated identity headers fail before command decoding without echoing their values. */
public class MutationRequestIdentityException internal constructor() :
    IllegalArgumentException("Invalid mutation request identity")

/** Copies established host security facts and adds the optional, strictly decoded mutation identity. */
public fun RequestContext.withMutationRequestIdentity(values: List<String>): RequestContext {
    val identity =
        if (values.isEmpty()) {
            null
        } else {
            val value = values.singleOrNull()?.let(RouteValues::uuid) ?: throw MutationRequestIdentityException()
            MutationRequestIdentity.of(value)
        }
    return RequestContext(method, trace, language, headers, cookies, security, identity)
}

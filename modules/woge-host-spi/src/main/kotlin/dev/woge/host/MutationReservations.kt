package dev.woge.host

import java.time.Instant
import java.util.UUID

/** Application-selected scope: action, authenticated subject and domain resource, never a browser field. */
public class MutationScope private constructor(
    public val value: String,
) {
    override fun equals(other: Any?): Boolean = other is MutationScope && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "MutationScope(<redacted>)"

    public companion object {
        public fun of(value: String): MutationScope {
            require(
                value.isNotBlank() &&
                    value.length <= MAX_SCOPE_LENGTH &&
                    value.none { it.code < ASCII_SPACE || it.code == DELETE },
            ) {
                "Mutation scope must be nonempty, bounded and free of control characters"
            }
            return MutationScope(value)
        }
    }
}

/** Digest of the application's canonical command; do not store submitted text in the replay store. */
public class MutationFingerprint private constructor(
    public val value: String,
) {
    override fun equals(other: Any?): Boolean = other is MutationFingerprint && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "MutationFingerprint(<redacted>)"

    public companion object {
        public fun ofSha256(value: String): MutationFingerprint {
            require(value.length == SHA256_HEX_LENGTH && value.all { it in '0'..'9' || it in 'a'..'f' }) {
                "Mutation fingerprint must be a lowercase SHA-256 digest"
            }
            return MutationFingerprint(value)
        }
    }
}

/**
 * Reservation keys are independent of domain ports and browser revisions.
 * Expiry/retention are fixed by the application when issuing the replay window, never renewed by a
 * duplicate request. Validate that window from trusted session state or a signed value before reserving.
 */
public class MutationReservationRequest(
    public val scope: MutationScope,
    public val identity: MutationRequestIdentity,
    public val fingerprint: MutationFingerprint,
    public val expiresAt: Instant,
    public val retainUntil: Instant,
) {
    init {
        require(retainUntil > expiresAt) { "Mutation retention must extend beyond identity expiry" }
    }

    override fun toString(): String =
        "MutationReservationRequest(scope=<redacted>, identity=<redacted>, fingerprint=<redacted>, " +
            "expiresAt=$expiresAt, retainUntil=$retainUntil)"
}

/** Opaque store-issued fencing token; a different attempt cannot resolve this reservation. */
public class MutationLease(
    public val token: UUID,
) {
    override fun toString(): String = "MutationLease(<redacted>)"
}

public enum class MutationState {
    IN_PROGRESS,
    COMPLETED,
    REJECTED,
    AMBIGUOUS,
    EXPIRED,
    CONFLICT,
}

public sealed interface MutationReservation {
    public class Acquired(
        public val lease: MutationLease,
    ) : MutationReservation

    public class Existing(
        public val state: MutationState,
    ) : MutationReservation
}

/** Explicit commit facts, not inferred from HTTP status or from whether a response was delivered. */
public enum class MutationResolution {
    COMPLETED,
    REJECTED,
    AMBIGUOUS,
}

/**
 * Optional application infrastructure. Reserve atomically by scope + identity and compare fingerprints.
 *
 * A duplicate never acquires another lease, even after expiry. Unknown expired identities return EXPIRED.
 * Changed fingerprints or replay-window timestamps return CONFLICT, not an extended reservation.
 * Retain terminal facts through retainUntil; an expired in-progress lease is AMBIGUOUS, never available.
 * Resolve must atomically fence by lease token and reject stale/conflicting resolutions. Storage errors
 * propagate; they are not permission to execute. Authorization must run before every reserve or lookup.
 */
public interface MutationReservationStore {
    public suspend fun reserve(
        request: MutationReservationRequest,
        now: Instant,
    ): MutationReservation

    public suspend fun resolve(
        request: MutationReservationRequest,
        lease: MutationLease,
        resolution: MutationResolution,
        now: Instant,
    )
}

private const val MAX_SCOPE_LENGTH = 256
private const val ASCII_SPACE = 32
private const val DELETE = 127
private const val SHA256_HEX_LENGTH = 64

package dev.woge.host

import dev.woge.protocol.PageEpoch
import dev.woge.protocol.Patch
import dev.woge.protocol.RegionTargetId
import dev.woge.protocol.TargetRevision
import kotlinx.coroutines.flow.Flow

/** An optional bidirectional channel. Every subscription and message needs application authorization. */
public interface WebSocketUseCase {
    /** Return null to refuse. Replay only contiguous patches after the supplied revisions, or refuse. */
    public suspend fun subscribe(
        request: WebSocketSubscription,
        context: RequestContext,
    ): Flow<Patch>?

    /** Ephemeral messages only; authorize again. Durable mutations belong to HTTP actions. */
    public suspend fun message(
        request: WebSocketSubscription,
        payload: String,
        context: RequestContext,
    )
}

/** One multiplexed subscription. IDs and revisions are untrusted input, never capabilities. */
public data class WebSocketSubscription(
    public val id: String,
    public val epoch: PageEpoch,
    public val revisions: Map<RegionTargetId, TargetRevision>,
)

/** Adapter-owned network I/O; sends must suspend until accepted by the transport. */
public interface WebSocketPeer {
    public val incoming: Flow<String>

    public suspend fun binary(bytes: ByteArray)

    public suspend fun text(value: String)

    public suspend fun close(
        code: Int,
        reason: String,
    )
}

/** Explicit upgrade decision. Verify authentication, endpoint permission and the session-bound proof. */
public fun interface WebSocketAuthorizer {
    public fun authorize(
        context: RequestContext,
        proof: String,
    ): Boolean
}

/** No permissive default: configure the public HTTP origin and an authorizer on every binding. */
public class WebSocketUpgradePolicy(
    private val origin: String,
    private val authorizer: WebSocketAuthorizer,
) {
    public fun accepts(
        context: RequestContext,
        requestOrigin: String?,
        protocols: List<String>,
    ): Boolean {
        val proofs = protocols.filter { it.startsWith(PROOF_PREFIX) }.map { it.removePrefix(PROOF_PREFIX) }
        return requestOrigin != null &&
            requestOrigin != "null" &&
            sameOriginCsrf(RequestMethod.POST, requestOrigin, null, origin) == CsrfVerification.VERIFIED &&
            protocols.count { it == WEB_SOCKET_PROTOCOL } == 1 &&
            proofs.size == 1 &&
            proofs.single().length in 1..SOCKET_MAX_PROOF &&
            proofs.single().all { socketIdCharacter(it) } &&
            authorizer.authorize(context, proofs.single())
    }
}

public const val WEB_SOCKET_PROTOCOL: String = "woge.patch.v1"
private const val PROOF_PREFIX: String = "woge.csrf."

package dev.woge.tck

import dev.woge.host.ActionExecutor
import dev.woge.host.FailureCategory
import dev.woge.host.MutationFingerprint
import dev.woge.host.MutationReservation
import dev.woge.host.MutationReservationRequest
import dev.woge.host.MutationResolution
import dev.woge.host.MutationScope
import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.ResponseHeaders
import dev.woge.host.failure
import dev.woge.host.httpHeader
import dev.woge.host.redirect
import dev.woge.html.applicationUrl
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

/** Separate test workflow; it never changes the ordinary action fixture's domain mutation count. */
internal class TckMutationWorkflow {
    private val store = InMemoryMutationStore()
    private val mutations = AtomicInteger()
    private val expiresAt = Instant.now().plusSeconds(REPLAY_WINDOW_SECONDS)
    private val retainUntil = expiresAt.plusSeconds(REPLAY_WINDOW_SECONDS)

    suspend fun execute(request: PageRequest<TckActionCommand>): PageResult {
        // The generated action checks current CSRF and domain authorization on every duplicate too.
        val authorized = TckSubmitAction.execute(request)
        if (authorized !is PageResult.Redirect) return authorized
        return reserveAuthorized(request)
    }

    private suspend fun reserveAuthorized(request: PageRequest<TckActionCommand>): PageResult {
        val identity =
            request.context.mutationIdentity
                ?: return failure(FailureCategory.BAD_REQUEST, request.context.correlationId)
        val now = Instant.now()
        val reservation =
            MutationReservationRequest(
                MutationScope.of("tck-user:fixture-board:mutation"),
                identity,
                MutationFingerprint.ofSha256(
                    MessageDigest.getInstance("SHA-256").digest(request.input.value.toByteArray()).toHexString(),
                ),
                expiresAt,
                retainUntil,
            )
        return when (val outcome = store.reserve(reservation, now)) {
            is MutationReservation.Existing ->
                redirect(
                    applicationUrl("/woge-tck/action-complete"),
                    headers = headers(outcome.state.name),
                )
            is MutationReservation.Acquired -> {
                if (request.input.value == "replay-in-progress") {
                    redirect(applicationUrl("/woge-tck/action-complete"), headers = headers("IN_PROGRESS"))
                } else {
                    val resolution =
                        when (request.input.value) {
                            "replay-rejected" -> MutationResolution.REJECTED
                            "replay-ambiguous" -> {
                                mutations.incrementAndGet()
                                MutationResolution.AMBIGUOUS
                            }
                            else -> {
                                mutations.incrementAndGet()
                                MutationResolution.COMPLETED
                            }
                        }
                    store.resolve(reservation, outcome.lease, resolution, Instant.now())
                    redirect(applicationUrl("/woge-tck/action-complete"), headers = headers(resolution.name))
                }
            }
        }
    }

    private fun headers(state: String): ResponseHeaders =
        ResponseHeaders.of(
            httpHeader("Woge-Test-Mutation-State", state),
            httpHeader("Woge-Test-Mutation-Count", mutations.get().toString()),
        )
}

/** Fresh request-replay fixture behind each host's actual token verification boundary. */
public fun tckSecurityAction(): ActionExecutor<TckActionCommand> {
    val mutations = TckMutationWorkflow()
    return ActionExecutor { request ->
        if (request.input.value.startsWith("replay-")) mutations.execute(request) else TckSubmitAction.execute(request)
    }
}

private const val REPLAY_WINDOW_SECONDS = 60L

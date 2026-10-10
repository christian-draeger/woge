@file:Suppress("MagicNumber")

package dev.woge.tck

import dev.woge.host.MutationFingerprint
import dev.woge.host.MutationLease
import dev.woge.host.MutationRequestIdentity
import dev.woge.host.MutationReservation
import dev.woge.host.MutationReservationRequest
import dev.woge.host.MutationResolution
import dev.woge.host.MutationScope
import dev.woge.host.MutationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Instant
import java.util.UUID

/** No sleeps: explicit time and concurrent reservations exercise the shared store contract. */
internal suspend fun verifyMutationStore() =
    coroutineScope {
        val store = InMemoryMutationStore()
        val now = Instant.parse("2026-10-10T16:00:00Z")
        val request = reservation(now)
        val attempts = (1..16).map { async(Dispatchers.Default) { store.reserve(request, now) } }.awaitAll()
        val lease = attempts.filterIsInstance<MutationReservation.Acquired>().single().lease
        check(attempts.filterIsInstance<MutationReservation.Existing>().all { it.state == MutationState.IN_PROGRESS })
        expectFencingFailure {
            store.resolve(
                request,
                MutationLease(UUID.randomUUID()),
                MutationResolution.COMPLETED,
                now,
            )
        }
        store.resolve(request, lease, MutationResolution.COMPLETED, now)
        check((store.reserve(request, now) as MutationReservation.Existing).state == MutationState.COMPLETED)
        expectFencingFailure { store.resolve(request, lease, MutationResolution.REJECTED, now) }
        check(
            (store.reserve(request, now.plusSeconds(61)) as MutationReservation.Existing).state ==
                MutationState.COMPLETED,
        )
        check(
            (store.reserve(request, now.plusSeconds(121)) as MutationReservation.Existing).state ==
                MutationState.EXPIRED,
        )

        val unfinished = reservation(now)
        store.reserve(unfinished, now)
        check(
            (store.reserve(unfinished, now.plusSeconds(61)) as MutationReservation.Existing).state ==
                MutationState.AMBIGUOUS,
        )
        val renewed =
            MutationReservationRequest(
                unfinished.scope,
                unfinished.identity,
                unfinished.fingerprint,
                now.plusSeconds(90),
                now.plusSeconds(150),
            )
        check((store.reserve(renewed, now) as MutationReservation.Existing).state == MutationState.CONFLICT)

        val otherScope =
            MutationReservationRequest(
                MutationScope.of("other-subject:other-resource"),
                request.identity,
                request.fingerprint,
                request.expiresAt,
                request.retainUntil,
            )
        check(store.reserve(otherScope, now) is MutationReservation.Acquired)
        for (resolution in listOf(MutationResolution.REJECTED, MutationResolution.AMBIGUOUS)) {
            val next = reservation(now)
            val acquired = store.reserve(next, now) as MutationReservation.Acquired
            store.resolve(next, acquired.lease, resolution, now)
            check((store.reserve(next, now) as MutationReservation.Existing).state.name == resolution.name)
        }
    }

private fun reservation(now: Instant): MutationReservationRequest =
    MutationReservationRequest(
        MutationScope.of("fixture-subject:fixture-resource"),
        MutationRequestIdentity.of(UUID.randomUUID()),
        MutationFingerprint.ofSha256("a".repeat(64)),
        now.plusSeconds(60),
        now.plusSeconds(120),
    )

private suspend fun expectFencingFailure(block: suspend () -> Unit) {
    try {
        block()
    } catch (_: IllegalStateException) {
        return
    }
    error("Store accepted a stale or conflicting resolution")
}

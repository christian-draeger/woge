package dev.woge.tck

import dev.woge.host.MutationLease
import dev.woge.host.MutationReservation
import dev.woge.host.MutationReservationRequest
import dev.woge.host.MutationReservationStore
import dev.woge.host.MutationResolution
import dev.woge.host.MutationState
import java.time.Instant
import java.util.UUID

/** Test-only atomic store. Production applications supply durable storage with the same fencing rules. */
internal class InMemoryMutationStore : MutationReservationStore {
    private class Entry(
        val request: MutationReservationRequest,
        val lease: MutationLease,
        var state: MutationState = MutationState.IN_PROGRESS,
    )

    private val entries = mutableMapOf<Pair<String, UUID>, Entry>()

    override suspend fun reserve(
        request: MutationReservationRequest,
        now: Instant,
    ): MutationReservation =
        synchronized(this) {
            entries.entries.removeIf { now >= it.value.request.retainUntil }
            val key = request.scope.value to request.identity.value
            val existing = entries[key]
            when {
                existing != null &&
                    (
                        existing.request.fingerprint != request.fingerprint ||
                            existing.request.expiresAt != request.expiresAt ||
                            existing.request.retainUntil != request.retainUntil
                    ) ->
                    MutationReservation.Existing(MutationState.CONFLICT)
                existing != null -> {
                    if (existing.state == MutationState.IN_PROGRESS && now >= existing.request.expiresAt) {
                        existing.state = MutationState.AMBIGUOUS
                    }
                    MutationReservation.Existing(existing.state)
                }
                now >= request.expiresAt -> MutationReservation.Existing(MutationState.EXPIRED)
                else -> {
                    check(entries.size < MAX_FIXTURE_RESERVATIONS) { "Fixture reservation capacity exhausted" }
                    val entry = Entry(request, MutationLease(UUID.randomUUID()))
                    entries[key] = entry
                    MutationReservation.Acquired(entry.lease)
                }
            }
        }

    override suspend fun resolve(
        request: MutationReservationRequest,
        lease: MutationLease,
        resolution: MutationResolution,
        now: Instant,
    ) {
        synchronized(this) {
            val entry = checkNotNull(entries[request.scope.value to request.identity.value]) { "Unknown reservation" }
            check(entry.lease.token == lease.token && entry.request.fingerprint == request.fingerprint) {
                "Reservation fencing failed"
            }
            check(entry.state == MutationState.IN_PROGRESS && now < entry.request.expiresAt) {
                "Reservation can no longer be resolved"
            }
            entry.state =
                when (resolution) {
                    MutationResolution.COMPLETED -> MutationState.COMPLETED
                    MutationResolution.REJECTED -> MutationState.REJECTED
                    MutationResolution.AMBIGUOUS -> MutationState.AMBIGUOUS
                }
        }
    }
}

private const val MAX_FIXTURE_RESERVATIONS = 128

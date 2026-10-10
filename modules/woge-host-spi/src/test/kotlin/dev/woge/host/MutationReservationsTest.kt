package dev.woge.host

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class MutationReservationsTest {
    @Test
    fun `reservation metadata requires explicit retention and redacts routing and command digests`() {
        val scope = MutationScope.of("private-subject:private-resource:save")
        val identity = MutationRequestIdentity.of(UUID.randomUUID())
        val fingerprint = MutationFingerprint.ofSha256("a".repeat(64))
        val expiry = Instant.parse("2026-10-10T16:00:00Z")
        val request = MutationReservationRequest(scope, identity, fingerprint, expiry, expiry.plusSeconds(60))
        for (value in listOf(scope.toString(), identity.toString(), fingerprint.toString(), request.toString())) {
            assertFalse(value.contains("private-subject"))
            assertFalse(value.contains(identity.value.toString()))
            assertFalse(value.contains(fingerprint.value))
        }
        assertEquals(expiry, request.expiresAt)
        assertThrows(IllegalArgumentException::class.java) {
            MutationReservationRequest(scope, identity, fingerprint, expiry, expiry)
        }
    }

    @Test
    fun `invalid scopes and noncanonical digests cannot enter store contracts`() {
        for (scope in listOf("", " ", "a".repeat(257), "scope\nforged")) {
            assertThrows(IllegalArgumentException::class.java) { MutationScope.of(scope) }
        }
        for (digest in listOf("", "A".repeat(64), "g".repeat(64), "a".repeat(63))) {
            assertThrows(IllegalArgumentException::class.java) { MutationFingerprint.ofSha256(digest) }
        }
    }
}

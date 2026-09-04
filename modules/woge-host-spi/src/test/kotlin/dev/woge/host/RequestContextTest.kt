package dev.woge.host

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RequestContextTest {
    @Test
    fun `request diagnostics redact cookie and principal values`() {
        val context =
            RequestContext(
                method = RequestMethod.GET,
                trace =
                    RequestTrace(
                        requestId = RequestId.of("request-123"),
                        correlationId = CorrelationId.of("trace-456"),
                    ),
                cookies = RequestCookies.of(requestCookie("session", "secret-cookie")),
                security =
                    RequestSecurity(
                        authentication =
                            AuthenticationFacts.Authenticated(
                                PrincipalFacts(PrincipalId.of("secret-subject")),
                            ),
                    ),
            )

        val diagnostic = context.toString()

        assertFalse(diagnostic.contains("secret-cookie"))
        assertFalse(diagnostic.contains("secret-subject"))
        assertTrue(diagnostic.contains("trace-456"))
        assertFalse(
            context.cookies
                .iterator()
                .next()
                .toString()
                .contains("secret-cookie"),
        )
    }

    @Test
    fun `principal capability snapshot cannot be mutated by the caller`() {
        val source = mutableSetOf(Capability.of("projects:read"))
        val principal = PrincipalFacts(PrincipalId.of("subject-1"), source)

        source.clear()

        assertTrue(principal.has(Capability.of("projects:read")))
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (principal.capabilities as MutableSet<Capability>).clear()
        }
    }

    @Test
    fun `request and correlation IDs reject diagnostic injection`() {
        assertThrows(IllegalArgumentException::class.java) {
            RequestId.of("request\nforged")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CorrelationId.of("trace with spaces")
        }
    }
}

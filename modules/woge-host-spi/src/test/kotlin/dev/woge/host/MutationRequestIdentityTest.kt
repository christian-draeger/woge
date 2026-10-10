package dev.woge.host

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

class MutationRequestIdentityTest {
    private val context =
        RequestContext(
            RequestMethod.POST,
            RequestTrace(RequestId.of("request"), CorrelationId.of("trace")),
            security = RequestSecurity(csrf = CsrfVerification.VERIFIED),
        )

    @Test
    fun `identity is strictly parsed independently of host trace and preserves security facts`() {
        val id = UUID.randomUUID()
        val copy = context.withMutationRequestIdentity(listOf(id.toString()))
        assertEquals(id, copy.mutationIdentity?.value)
        assertSame(context.security, copy.security)
        assertSame(context.trace, copy.trace)
        assertNull(context.mutationIdentity)
        assertNull(context.withMutationRequestIdentity(emptyList()).mutationIdentity)
        assertFalse(copy.toString().contains(id.toString()))
        assertFalse(copy.mutationIdentity.toString().contains(id.toString()))
    }

    @Test
    fun `invalid empty merged and repeated identities fail without submitted values`() {
        val id = UUID.randomUUID().toString()
        for (values in listOf(listOf(""), listOf("1-1-1-1-1"), listOf("secret"), listOf("$id,$id"), listOf(id, id))) {
            val failure =
                assertThrows(MutationRequestIdentityException::class.java) {
                    context.withMutationRequestIdentity(values)
                }
            assertEquals("Invalid mutation request identity", failure.message)
        }
    }
}

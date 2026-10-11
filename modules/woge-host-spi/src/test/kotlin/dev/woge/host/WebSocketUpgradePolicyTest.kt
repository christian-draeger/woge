package dev.woge.host

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WebSocketUpgradePolicyTest {
    @Test
    fun `origin protocol and verified session proof are mandatory at upgrade`() {
        val context =
            RequestContext(RequestMethod.GET, RequestTrace(RequestId.of("socket"), CorrelationId.of("socket")))
        var calls = 0
        val policy =
            WebSocketUpgradePolicy(
                "https://example.test",
                WebSocketAuthorizer { _, proof ->
                    calls++
                    proof == "good-proof"
                },
            )
        val protocols = listOf(WEB_SOCKET_PROTOCOL, "woge.csrf.good-proof")
        assertFalse(policy.accepts(context, null, protocols))
        assertFalse(policy.accepts(context, "null", protocols))
        assertFalse(policy.accepts(context, "https://evil.test", protocols))
        assertFalse(policy.accepts(context, "https://example.test", protocols + "woge.csrf.other"))
        assertFalse(policy.accepts(context, "https://example.test", listOf("woge.csrf.good-proof")))
        assertTrue(calls == 0)
        assertFalse(policy.accepts(context, "https://example.test", listOf(WEB_SOCKET_PROTOCOL, "woge.csrf.bad-proof")))
        assertTrue(policy.accepts(context, "https://example.test", protocols))
        assertTrue(calls == 2)
    }
}

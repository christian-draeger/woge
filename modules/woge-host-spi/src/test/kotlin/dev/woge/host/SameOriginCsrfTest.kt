package dev.woge.host

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SameOriginCsrfTest {
    @Test
    fun `safe methods do not require verification`() {
        listOf(RequestMethod.GET, RequestMethod.HEAD, RequestMethod.OPTIONS).forEach { method ->
            assertEquals(
                CsrfVerification.NOT_REQUIRED,
                sameOriginCsrf(method, "https://evil.test", null, REQUEST_ORIGIN),
            )
        }
    }

    @Test
    fun `matching origins ignore scheme and host case and normalize default ports`() {
        assertVerified("https://APP.example.test", "https://app.example.test:443")
        assertEquals(
            CsrfVerification.VERIFIED,
            sameOriginCsrf(RequestMethod.POST, "http://app.example.test:80", null, "http://APP.example.test"),
        )
    }

    @Test
    fun `non-default ports and different origins are refused`() {
        assertRefused("https://app.example.test:444", REQUEST_ORIGIN)
        assertRefused("http://app.example.test", REQUEST_ORIGIN)
        assertRefused("https://other.example.test", REQUEST_ORIGIN)
    }

    @Test
    fun `a present mismatched origin cannot be rescued by fetch metadata`() {
        assertEquals(
            CsrfVerification.NOT_REQUIRED,
            sameOriginCsrf(RequestMethod.POST, "https://evil.test", "same-origin", REQUEST_ORIGIN),
        )
    }

    @Test
    fun `same origin fetch metadata verifies when origin is absent or null`() {
        assertVerified(null, "same-origin")
        assertVerified("null", "same-origin")
        assertVerified(null, "SAME-ORIGIN")
    }

    @Test
    fun `missing or malformed origin without same origin fetch metadata is refused`() {
        assertRefused(null, null)
        assertRefused("null", null)
        assertRefused("not an origin", null)
        assertRefused("https://app.example.test/path", null)
        assertRefused("https://user@app.example.test", null)
        assertRefused("https://app.example.test?query", null)
    }

    private fun assertVerified(
        origin: String?,
        fetchSite: String? = null,
    ) {
        assertEquals(CsrfVerification.VERIFIED, sameOriginCsrf(RequestMethod.POST, origin, fetchSite, REQUEST_ORIGIN))
    }

    private fun assertRefused(
        origin: String?,
        fetchSite: String?,
    ) {
        assertEquals(
            CsrfVerification.NOT_REQUIRED,
            sameOriginCsrf(RequestMethod.POST, origin, fetchSite, REQUEST_ORIGIN),
        )
    }

    private companion object {
        const val REQUEST_ORIGIN = "https://app.example.test"
    }
}

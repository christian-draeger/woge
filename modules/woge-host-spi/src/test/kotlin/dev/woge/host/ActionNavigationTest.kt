package dev.woge.host

import dev.woge.html.applicationUrl
import dev.woge.html.externalUrl
import dev.woge.protocol.PatchStreamV1
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ActionNavigationTest {
    @Test
    fun `only explicit current patch requests translate canonical application redirects`() {
        val result = redirect(applicationUrl("/tasks"))
        listOf(
            PatchStreamV1.MEDIA_TYPE,
            "application/vnd.woge.patch-stream;version=1",
            """APPLICATION/VND.WOGE.PATCH-STREAM ; version = "1" """,
        ).forEach { assertEquals("/tasks", result.enhancedActionNavigation(it)?.value) }
        listOf(
            null,
            "",
            "*/*",
            "text/html",
            "application/vnd.woge.patch-stream; version=2",
            "${PatchStreamV1.MEDIA_TYPE}; q=0",
        ).forEach {
            assertNull(result.enhancedActionNavigation(it))
        }
    }

    @Test
    fun `method preserving and external redirects never become enhanced navigation`() {
        listOf(ResponseStatus.TEMPORARY_REDIRECT, ResponseStatus.PERMANENT_REDIRECT).forEach { status ->
            assertNull(redirect(applicationUrl("/tasks"), status).enhancedActionNavigation(PatchStreamV1.MEDIA_TYPE))
        }
        val external = externalRedirect(externalUrl("https://example.com/tasks"), ExternalRedirectPolicy { true })
        assertNull(external.enhancedActionNavigation(PatchStreamV1.MEDIA_TYPE))
    }

    @Test
    fun `unsupported explicit patch versions can be rejected without treating native forms as protocol requests`() {
        assertTrue(requestsUnsupportedActionPatchVersion("application/vnd.woge.patch-stream; version=2"))
        assertTrue(requestsUnsupportedActionPatchVersion("application/vnd.woge.patch-stream; version=\"2\""))
        assertFalse(requestsUnsupportedActionPatchVersion("application/vnd.woge.patch-stream; version=1"))
        assertFalse(requestsUnsupportedActionPatchVersion("*/*"))
        assertFalse(requestsUnsupportedActionPatchVersion("text/html"))
    }
}

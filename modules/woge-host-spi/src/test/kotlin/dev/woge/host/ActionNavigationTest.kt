package dev.woge.host

import dev.woge.html.applicationUrl
import dev.woge.html.externalUrl
import dev.woge.protocol.PatchStreamV1
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ActionNavigationTest {
    @Test
    fun `only explicit current patch requests translate canonical application redirects`() {
        val result = redirect(applicationUrl("/tasks"))
        assertEquals("/tasks", result.enhancedActionNavigation(PatchStreamV1.MEDIA_TYPE)?.value)
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
}

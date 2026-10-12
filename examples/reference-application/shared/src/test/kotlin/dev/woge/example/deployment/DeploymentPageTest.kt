package dev.woge.example.deployment

import dev.woge.host.CorrelationId
import dev.woge.host.CsrfVerification
import dev.woge.host.FailureCategory
import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.RenderIdentitySecret
import dev.woge.host.RenderSessions
import dev.woge.host.RequestContext
import dev.woge.host.RequestId
import dev.woge.host.RequestMethod
import dev.woge.host.RequestSecurity
import dev.woge.host.RequestTrace
import dev.woge.host.withRenderSession
import dev.woge.html.HtmlSink
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeploymentPageTest {
    @Test
    fun `a resumed old document has the same target on a replacement process and escapes form text`() =
        runTest {
            val secret = RenderIdentitySecret.of(ByteArray(32) { it.toByte() })
            val old = RenderSessions(secret).create().resume("old-document")
            val next = RenderSessions(secret).create().resume("old-document")
            val document =
                deploymentPage.open(
                    PageRequest(DeploymentInput, context().withRenderSession(old)),
                ) as PageResult.Document
            val output = StringBuilder()
            document.frames.collect { it.writeTo(HtmlSink(output::append)) }
            val html = output.toString()
            val result =
                deploymentConfirm(
                    DeploymentCommand("<script>alert(1)</script>"),
                    context().withRenderSession(next),
                ) as PageResult.RegionUpdates
            val patch = result.patches.single()
            assertTrue(html.contains("data-woge-region=\"${patch.target.region.value}\""))
            assertEquals("old-document", patch.target.pageEpoch.value)
            assertEquals("<p>&lt;script&gt;alert(1)&lt;/script&gt;</p>", patch.html.value)
            assertEquals("/deployment", (result.nativeResult as PageResult.Redirect).location.value)
            assertTrue(html.contains("Open a fresh page"))
        }

    @Test
    fun `confirmation requires CSRF and bounded input before producing updates`() =
        runTest {
            val forbidden =
                deploymentConfirm(
                    DeploymentCommand("Hello"),
                    context(CsrfVerification.NOT_REQUIRED),
                ) as PageResult.Failure
            assertEquals(FailureCategory.FORBIDDEN, forbidden.failure.category)
            for (text in listOf("", "x".repeat(121))) {
                val failure = deploymentConfirm(DeploymentCommand(text), context()) as PageResult.Failure
                assertEquals(FailureCategory.BAD_REQUEST, failure.failure.category)
            }
        }

    private fun context(csrf: CsrfVerification = CsrfVerification.VERIFIED): RequestContext =
        RequestContext(
            RequestMethod.POST,
            RequestTrace(RequestId.of("deployment-test"), CorrelationId.of("deployment-test")),
            security = RequestSecurity(csrf = csrf),
        )
}

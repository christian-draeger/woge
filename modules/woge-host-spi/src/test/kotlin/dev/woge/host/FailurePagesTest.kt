package dev.woge.host

import dev.woge.html.BufferedHtmlSink
import dev.woge.html.applicationUrl
import dev.woge.protocol.htmlFrame
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class FailurePagesTest {
    @Test
    fun `configured HTML preserves every failure status and safe metadata`() =
        runBlocking {
            FailureCategory.entries.forEach { category ->
                val original = failure(category, CorrelationId.of("trace-42"))
                val result =
                    original.withFailurePages(
                        FailurePages { safe ->
                            assertEquals(original.failure, safe)
                            htmlFrame { element("p") { text("Reference <${safe.correlationId.value}>") } }
                        },
                    )
                val document = assertInstanceOf(PageResult.Document::class.java, result)
                assertEquals(category.status, document.metadata.status)
                assertEquals(ContentType.HTML_UTF_8, document.metadata.contentType)
                assertSame(original.metadata.headers, document.metadata.headers)
                assertEquals(original.metadata.cookies, document.metadata.cookies)
                val sink = BufferedHtmlSink()
                document.writeTo(sink)
                assertEquals("<p>Reference &lt;trace-42&gt;</p>", sink.content())
            }
        }

    @Test
    fun `default and declined failure pages keep the original bodyless result`() {
        val result = failure(FailureCategory.NOT_FOUND, CorrelationId.of("trace-42"))
        assertSame(result, result.withFailurePages(FailurePages.NONE))
        assertSame(result, result.withFailurePages(FailurePages { null }))
    }

    @Test
    fun `documents and redirects never invoke failure rendering`() {
        val pages = FailurePages { error("must not render") }
        val document = htmlPage { text("OK") }
        val redirect = redirect(applicationUrl("/"))
        assertSame(document, document.withFailurePages(pages))
        assertSame(redirect, redirect.withFailurePages(pages))
    }

    @Test
    fun `failure renderer errors propagate rather than falling back silently`() {
        val result = failure(FailureCategory.INTERNAL, CorrelationId.of("trace-42"))
        val cause = IllegalStateException("renderer failed")
        val thrown =
            assertThrows(IllegalStateException::class.java) {
                result.withFailurePages(FailurePages { throw cause })
            }
        assertSame(cause, thrown)
    }
}

package dev.woge.host

import dev.woge.html.applicationUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class HttpCachingTest {
    @Test
    fun `RFC850 dates beyond fifty years in the future refer to the previous century`() {
        val modified =
            ZonedDateTime
                .now(ZoneOffset.UTC)
                .plusYears(50)
                .plusDays(1)
                .minusYears(100)
                .withNano(0)
        val document =
            htmlPage(
                ResponseMetadata(
                    headers =
                        ResponseHeaders.of(
                            httpHeader("Cache-Control", "private, no-cache"),
                            httpHeader("Last-Modified", modified.format(DateTimeFormatter.RFC_1123_DATE_TIME)),
                        ),
                ),
            ) {}
        val condition = modified.format(DateTimeFormatter.ofPattern("EEEE, dd-MMM-yy HH:mm:ss 'GMT'", Locale.US))
        assertTrue(
            document.forHttpRequest(RequestMethod.GET, ifModifiedSince = listOf(condition)) is PageResult.NotModified,
        )
    }

    @Test
    fun `default pages actions errors and cookie responses are never stored`() {
        assertNoStore(htmlPage {}.forHttpRequest(RequestMethod.GET))
        assertNoStore(page().forHttpRequest(RequestMethod.POST, listOf("\"version,one\"")))
        assertNoStore(page(ResponseStatus.NOT_FOUND).forHttpRequest(RequestMethod.GET))
        val cookiePage =
            htmlPage(
                ResponseMetadata(
                    headers = ResponseHeaders.of(httpHeader("Cache-Control", "public, max-age=600")),
                    cookies = listOf(responseCookie("session", "secret")),
                ),
            ) {}
        assertNoStore(cookiePage.forHttpRequest(RequestMethod.GET))
        assertNoStore(redirect(applicationUrl("/home")).forHttpRequest(RequestMethod.POST))
    }

    @Test
    fun `weak tags wildcard and quoted commas work for GET and HEAD`() {
        for (method in listOf(RequestMethod.GET, RequestMethod.HEAD)) {
            for (condition in listOf(
                listOf("\"version,one\""),
                listOf("W/\"version,one\""),
                listOf("\"different\"", "W/\"version,one\""),
                listOf("*"),
            )) {
                val result = page().forHttpRequest(method, condition)
                assertTrue(result is PageResult.NotModified, condition.toString())
                assertEquals(ResponseStatus.NOT_MODIFIED, result.metadata.status)
                assertEquals(null, result.metadata.contentType)
                assertEquals("private, no-cache", result.header("Cache-Control"))
                assertEquals("\"version,one\"", result.header("ETag"))
                assertEquals("Accept-Language", result.header("Vary"))
            }
        }
    }

    @Test
    fun `invalid or nonmatching entity tags take precedence over date`() {
        for (condition in listOf(
            "\"different\"",
            "",
            "version",
            "\"unfinished",
            "\"version,one\",",
            "*, \"version,one\"",
            "\"version,one\" junk",
            "\"x\u0000\"",
            "x".repeat(8193),
        )) {
            assertTrue(page().forHttpRequest(RequestMethod.GET, listOf(condition), listOf(DATE)) is PageResult.Document)
        }
    }

    @Test
    fun `modified since applies only without entity tag conditions`() {
        for (date in listOf(
            DATE,
            "Sat, 10 Oct 2026 17:00:00 GMT",
            "Saturday, 10-Oct-26 16:00:00 GMT",
            "Sat Oct 10 16:00:00 2026",
        )) {
            assertTrue(
                page().forHttpRequest(RequestMethod.GET, ifModifiedSince = listOf(date)) is PageResult.NotModified,
            )
        }
        for (dates in listOf(listOf("bad"), listOf("Fri, 9 Oct 2026 17:00:00 GMT"), listOf(DATE, DATE))) {
            assertTrue(page().forHttpRequest(RequestMethod.GET, ifModifiedSince = dates) is PageResult.Document)
        }
        val noStore =
            htmlPage(
                ResponseMetadata(
                    headers =
                        ResponseHeaders.of(
                            httpHeader("Cache-Control", "no-store"),
                            httpHeader("ETag", "\"version,one\""),
                        ),
                ),
            ) {}
        assertTrue(noStore.forHttpRequest(RequestMethod.GET, listOf("*")) is PageResult.Document)
    }

    private fun page(status: ResponseStatus = ResponseStatus.OK) =
        htmlPage(
            ResponseMetadata(
                status = status,
                headers =
                    ResponseHeaders.of(
                        httpHeader("Cache-Control", "private, no-cache"),
                        httpHeader("ETag", "\"version,one\""),
                        httpHeader("Last-Modified", DATE),
                        httpHeader("Vary", "Accept-Language"),
                    ),
            ),
        ) { error("Conditional metadata must never render a frame") }

    private fun assertNoStore(result: PageResult) {
        assertEquals("no-store", result.header("Cache-Control"))
        assertEquals(null, result.header("ETag"))
        assertEquals(null, result.header("Last-Modified"))
    }

    private fun PageResult.header(name: String): String? =
        metadata.headers
            .values(HeaderName.of(name))
            .singleOrNull()
            ?.value

    private companion object {
        const val DATE = "Sat, 10 Oct 2026 16:00:00 GMT"
    }
}

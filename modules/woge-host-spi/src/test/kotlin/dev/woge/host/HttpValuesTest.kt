package dev.woge.host

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HttpValuesTest {
    @Test
    fun `header names are normalized and values reject response splitting`() {
        val header = httpHeader("X-Request-Mode", "enhanced")

        assertEquals("x-request-mode", header.name.value)
        assertTrue(!header.toString().contains("enhanced"))
        assertThrows(IllegalArgumentException::class.java) {
            httpHeader("x-safe", "ok\r\nset-cookie: stolen=yes")
        }
    }

    @Test
    fun `response headers cannot override typed or adapter-owned metadata`() {
        val forbiddenNames =
            listOf(
                "content-type",
                "content-length",
                "location",
                "set-cookie",
                "connection",
                "transfer-encoding",
            )

        forbiddenNames.forEach { name ->
            assertThrows(IllegalArgumentException::class.java) {
                ResponseHeaders.of(httpHeader(name, "value"))
            }
        }
    }

    @Test
    fun `response cookies use conservative defaults`() {
        val cookie = responseCookie("session", "opaque-token")

        assertTrue(cookie.secure)
        assertTrue(cookie.httpOnly)
        assertEquals(SameSite.LAX, cookie.sameSite)
        assertEquals("/", cookie.path.value)
        assertThrows(IllegalArgumentException::class.java) {
            cookie.copy(secure = false, sameSite = SameSite.NONE)
        }
    }

    @Test
    fun `metadata snapshots response cookies`() {
        val source = mutableListOf(responseCookie("theme", "dark"))
        val metadata = ResponseMetadata(cookies = source)

        source.clear()

        assertEquals(1, metadata.cookies.size)
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (metadata.cookies as MutableList<ResponseCookie>).clear()
        }
    }
}

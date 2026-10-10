package dev.woge.spring.mvc

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.multipart.support.StandardServletMultipartResolver

class WogeMultipartResolverTest {
    @Test
    fun `only registered Woge paths bypass eager Spring multipart parsing including context paths`() {
        val resolver = WogeMultipartResolver(setOf("/woge-actions/upload"), StandardServletMultipartResolver())
        val request =
            MockHttpServletRequest().apply {
                contentType = "multipart/form-data; boundary=native"
                contextPath = "/app"
                requestURI = "/app/woge-actions/upload"
            }
        assertFalse(resolver.isMultipart(request))
        request.requestURI = "/app/ordinary-spring-upload"
        assertTrue(resolver.isMultipart(request))
        request.contentType = "application/x-www-form-urlencoded"
        assertFalse(resolver.isMultipart(request))
        assertThrows(IllegalArgumentException::class.java) { WogeMultipartResolver(emptySet()) }
        assertThrows(IllegalArgumentException::class.java) { WogeMultipartResolver(setOf("not-absolute")) }
    }
}

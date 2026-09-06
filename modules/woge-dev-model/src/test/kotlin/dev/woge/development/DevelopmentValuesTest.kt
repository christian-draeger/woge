package dev.woge.development

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalWogeDevelopmentApi::class)
class DevelopmentValuesTest {
    @Test
    fun `identifiers are positive monotonic and overflow safe`() {
        assertEquals(BuildId.of(2), BuildId.after(BuildId.FIRST))
        assertEquals(ServerGeneration.of(2), ServerGeneration.after(ServerGeneration.FIRST))
        assertThrows(IllegalArgumentException::class.java) { BuildId.of(0) }
        assertThrows(IllegalArgumentException::class.java) { ServerGeneration.of(-1) }
        assertThrows(IllegalArgumentException::class.java) { BuildId.after(BuildId.of(Long.MAX_VALUE)) }
        assertThrows(IllegalArgumentException::class.java) {
            ServerGeneration.after(ServerGeneration.of(Long.MAX_VALUE))
        }
    }

    @Test
    fun `reload levels form the complete correctness fallback chain`() {
        assertEquals(ReloadLevel.HOT_FRONTEND_MODULE, ReloadLevel.HOT_ASSET.fallback())
        assertEquals(ReloadLevel.DOCUMENT_REFRESH, ReloadLevel.HOT_FRONTEND_MODULE.fallback())
        assertEquals(ReloadLevel.SERVER_RESTART, ReloadLevel.DOCUMENT_REFRESH.fallback())
        assertEquals(ReloadLevel.COLD_RESTART, ReloadLevel.SERVER_RESTART.fallback())
        assertNull(ReloadLevel.COLD_RESTART.fallback())

        assertTrue(ReloadLevel.DOCUMENT_REFRESH.satisfies(ReloadLevel.HOT_ASSET))
        assertFalse(ReloadLevel.HOT_ASSET.satisfies(ReloadLevel.DOCUMENT_REFRESH))
    }

    @Test
    fun `development URLs are loopback only`() {
        assertEquals(
            "http://localhost:8080/app",
            DevelopmentUrl.local("http://localhost:8080/app").value,
        )
        assertEquals("https://ui.localhost:8443/", DevelopmentUrl.local("https://ui.localhost:8443/").value)
        assertEquals("http://[::1]:8080/", DevelopmentUrl.local("http://[::1]:8080/").value)

        assertThrows(IllegalArgumentException::class.java) {
            DevelopmentUrl.local("http://192.168.1.4:8080/")
        }
        assertThrows(IllegalArgumentException::class.java) { DevelopmentUrl.local("http://example.com/") }
        assertThrows(IllegalArgumentException::class.java) { DevelopmentUrl.local("file:///tmp/index.html") }
        assertThrows(IllegalArgumentException::class.java) {
            DevelopmentUrl.local("http://user@localhost:8080/")
        }
        assertThrows(IllegalArgumentException::class.java) {
            DevelopmentUrl.local("http://localhost:8080/?token=secret")
        }
    }

    @Test
    fun `diagnostics expose structured relative locations and redact string rendering`() {
        val diagnostic =
            DevelopmentDiagnostic(
                code = DevelopmentDiagnosticCode.of("WOGE-KOTLIN-COMPILE"),
                severity = DevelopmentDiagnosticSeverity.ERROR,
                summary = DevelopmentDiagnosticSummary.of("Type mismatch"),
                location = DevelopmentSourceLocation(DevelopmentSourcePath.of("src/main/App.kt"), 12, 8),
            )

        assertTrue(diagnostic.toString().contains("WOGE-KOTLIN-COMPILE"))
        assertFalse(diagnostic.toString().contains("Type mismatch"))
        assertThrows(IllegalArgumentException::class.java) { DevelopmentSourcePath.of("/Users/me/App.kt") }
        assertThrows(IllegalArgumentException::class.java) { DevelopmentSourcePath.of("src/../secret.txt") }
        assertThrows(IllegalArgumentException::class.java) { DevelopmentDiagnosticSummary.of("line one\nline two") }
    }
}

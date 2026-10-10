package dev.woge.host

import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ApplicationManifestTest {
    @Test
    fun `public model reads structural descriptors and additive schema fields`() {
        val manifest = ApplicationManifest.decode(SOURCE)
        assertEquals(ApplicationManifest.SCHEMA_VERSION, manifest.schemaVersion)
        assertEquals(ManifestHostAdapter.SPRING_WEBFLUX, manifest.hostAdapter)
        assertEquals(
            listOf(DescriptorMetadata(DescriptorKind.PAGE, "/", "example.HomeRoute", path = "/")),
            manifest.descriptors,
        )
    }

    @Test
    fun `unsupported schema missing required fields and unknown descriptor kinds fail explicitly`() {
        assertThrows(IllegalArgumentException::class.java) {
            ApplicationManifest.decode(SOURCE.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"))
        }
        assertThrows(SerializationException::class.java) {
            ApplicationManifest.decode(SOURCE.replace("\"wogeVersion\": \"0.1.0\",", ""))
        }
        assertThrows(SerializationException::class.java) {
            ApplicationManifest.decode(SOURCE.replace("\"kind\": \"page\"", "\"kind\": \"unknown\""))
        }
        assertThrows(SerializationException::class.java) {
            ApplicationManifest.decode(
                SOURCE.replace("\"hostAdapter\": \"spring-webflux\"", "\"hostAdapter\": \"unknown\""),
            )
        }
    }

    private companion object {
        val SOURCE =
            """
            {
              "schemaVersion": 1,
              "wogeVersion": "0.1.0",
              "kotlinVersion": "2.4.10",
              "hostAdapter": "spring-webflux",
              "capabilities": ["pages"],
              "frontendMode": "server-html",
              "documentation": {"home": "https://github.com/christian-draeger/woge"},
              "futureAddition": "ignored",
              "descriptors": [{"kind": "page", "id": "/", "declaration": "example.HomeRoute", "path": "/"}]
            }
            """.trimIndent()
    }
}

package dev.woge.host

import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import java.nio.file.Path

class UploadPolicyTest {
    @Test
    fun `authorization precedes file creation and cleanup hook runs on rejection`() {
        val store = TrackingFiles()
        val policy =
            UploadPolicy(temporaryFiles = { store }, authorize = {
                assertEquals("../../untrusted", it.filename)
                false
            })
        val failure =
            assertThrows<UploadDecodingException> {
                FormDecoder(Command.serializer()).multipartBody(TYPE, policy).use { it.accept(BODY.toByteArray()) }
            }
        assertEquals(FailureCategory.FORBIDDEN, failure.category)
        assertEquals(0, store.created)
        assertEquals(1, store.closed)
    }

    @Test
    fun `custom storage closes exactly once after success partial input and size rejection`() {
        val directory = Path.of("build", "upload-policy-tests")
        Files.createDirectories(directory)
        try {
            for (mode in listOf("success", "partial", "oversize")) {
                verifyStorageLifecycle(directory, mode)
            }
        } finally {
            Files.delete(directory)
        }
    }

    private fun verifyStorageLifecycle(
        directory: Path,
        mode: String,
    ) {
        val store = TrackingFiles(DirectoryUploadTemporaryFiles(directory))
        val policy =
            UploadPolicy(
                limits = UploadLimits(fileBytes = if (mode == "oversize") 1 else 8),
                temporaryFiles = { store },
            )
        val parser = FormDecoder(Command.serializer()).multipartBody(TYPE, policy)
        try {
            if (mode == "oversize") {
                assertThrows<UploadDecodingException> { parser.accept(BODY.toByteArray()) }
            } else if (mode == "partial") {
                parser.accept(BODY.substringBefore("\r\n--p--").toByteArray())
            } else {
                parser.accept(BODY.toByteArray())
                val submission = parser.finish()
                assertEquals(0, store.closed)
                assertEquals("abc", submission.files.single().read { String(it.readAllBytes()) })
                submission.close()
                submission.close()
            }
        } finally {
            parser.close()
        }
        assertEquals(1, store.created)
        assertEquals(1, store.closed)
        assertEquals(0L, Files.list(directory).use { it.count() })
    }

    private class TrackingFiles(
        private val delegate: UploadTemporaryFiles? = null,
    ) : UploadTemporaryFiles {
        var created = 0
        var closed = 0

        override fun create(): Path {
            created++
            return checkNotNull(delegate).create()
        }

        override fun close() {
            closed++
            delegate?.close()
        }
    }

    @Serializable
    private data class Command(
        val title: String = "",
    )
}

private const val TYPE = "multipart/form-data; boundary=p"
private const val BODY =
    "--p\r\nContent-Disposition: form-data; name=\"file\"; filename=\"../../untrusted\"\r\n" +
        "\r\nabc\r\n--p--\r\n"

package dev.woge.host

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class MultipartBodyTest {
    @TempDir
    lateinit var directory: Path

    private val decoder = FormDecoder(Command.serializer())

    @Test
    fun `every split preserves UTF-8 text untrusted file metadata and binary boundary lookalikes`() {
        val data =
            byteArrayOf(0, -1, 42) + "\r\n--boundaryX\r\n--boundar\r\n--boundary".toByteArray() +
                byteArrayOf(-1, 42)
        val wire = body(data)
        for (split in 0..wire.size) {
            decoder.multipartBody(TYPE, temporaryDirectory = directory).use { parser ->
                parser.accept(wire, length = split)
                parser.accept(wire, offset = split)
                parser.finish().use { submission ->
                    assertEquals(Command("\u00e9<&"), submission.form.result.getOrThrow())
                    val file = submission.files("attachment").single()
                    assertEquals("../../private;name.txt", file.filename)
                    assertEquals("application/octet-stream", file.mediaType)
                    assertEquals(data.size.toLong(), file.size)
                    assertArrayEquals(data, file.read { it.readAllBytes() })
                    assertFalse(file.toString().contains("private"))
                    assertFalse(submission.toString().contains("\u00e9"))
                }
            }
            assertEmpty()
        }
    }

    @Test
    fun `one byte chunks exact allowances and empty uploads have deterministic cleanup`() {
        val wire = body(byteArrayOf(1, 2, 3))
        decoder
            .multipartBody(
                TYPE,
                UploadLimits(requestBytes = wire.size.toLong(), fileBytes = 3, temporaryBytes = 3),
                directory,
            ).use { parser ->
                wire.forEach { parser.accept(byteArrayOf(it)) }
                val submission = parser.finish()
                val file = submission.files.single()
                submission.close()
                submission.close()
                assertThrows(IllegalStateException::class.java) { file.read { it.read() } }
            }
        assertEmpty()
        decoder.multipartBody(TYPE, temporaryDirectory = directory).use { parser ->
            parser.accept("--boundary--\r\n".toByteArray())
            parser.finish().use { assertTrue(it.files.isEmpty()) }
        }
        decoder.multipartBody(TYPE, temporaryDirectory = directory).use { parser ->
            parser.accept(body(byteArrayOf()))
            parser.finish().use { assertEquals(0, it.files.single().size) }
        }
        assertEmpty()
    }

    @Test
    fun `all upload allowances reject incrementally and never leak files or payload diagnostics`() {
        val wire = body(byteArrayOf(1, 2, 3))
        val cases =
            listOf(
                Triple(UploadLimits(requestBytes = wire.size - 1L), UploadLimit.REQUEST_BYTES, wire.size - 1L),
                Triple(UploadLimits(fileBytes = 2), UploadLimit.FILE_BYTES, 2L),
                Triple(UploadLimits(temporaryBytes = 2), UploadLimit.TEMPORARY_BYTES, 2L),
                Triple(UploadLimits(headerBytes = 4), UploadLimit.HEADER_BYTES, 4L),
            )
        for ((limits, name, threshold) in cases) {
            val rejected =
                assertThrows(UploadDecodingException::class.java) {
                    decoder.multipartBody(TYPE, limits, directory).use { parser ->
                        parser.accept(wire)
                        parser.finish().close()
                    }
                }
            assertEquals(FailureCategory.PAYLOAD_TOO_LARGE, rejected.category)
            assertEquals(name, rejected.limit)
            assertEquals(threshold, rejected.threshold)
            assertFalse(rejected.message.orEmpty().contains("private"))
            assertEmpty()
        }
    }

    @Test
    fun `malformed headers truncated files and non UTF-8 text are refused and cleaned`() {
        val wire = body(byteArrayOf(1, 2))
        val invalid =
            listOf(
                wire.copyOf(wire.size - 10),
                wire + byteArrayOf(1),
                body(byteArrayOf())
                    .toString(Charsets.UTF_8)
                    .replace("name=\"title\"", "name=\"title\"; name=\"other\"")
                    .toByteArray(),
                body(byteArrayOf())
                    .toString(Charsets.UTF_8)
                    .replace("filename=\"", "filename*=\"")
                    .toByteArray(),
                "--boundary\r\nContent-Disposition: form-data; name=\"title\"\r\n\r\n".toByteArray() +
                    byteArrayOf(-1) + "\r\n--boundary--\r\n".toByteArray(),
            )
        invalid.forEach { bytes ->
            assertThrows(UploadDecodingException::class.java) {
                decoder.multipartBody(TYPE, temporaryDirectory = directory).use { parser ->
                    parser.accept(bytes)
                    parser.finish().close()
                }
            }
            assertEmpty()
        }
    }

    @Test
    fun `file metadata never chooses a storage path and cleanup runs when application throws or cancels`() {
        for (failure in listOf(IllegalStateException("application failed"), CancellationException("cancelled"))) {
            val submission =
                decoder.multipartBody(TYPE, temporaryDirectory = directory).use { parser ->
                    parser.accept(body(byteArrayOf(1, 2)))
                    parser.finish()
                }
            val context =
                RequestContext(RequestMethod.POST, RequestTrace(RequestId.of("upload"), CorrelationId.of("upload")))
            val thrown =
                runCatching {
                    runBlocking {
                        PageRequest(submission, context).withUploadCleanup { throw failure }
                    }
                }.exceptionOrNull()
            assertEquals(failure, thrown)
            assertEmpty()
        }
    }

    @Test
    fun `multipart text uses ordinary typed errors and positive limits are required`() {
        val strict = FormDecoder(Command.serializer(), FormLimits(valueBytes = 1))
        assertThrows(FormDecodingException::class.java) {
            strict.multipartBody(TYPE, temporaryDirectory = directory).use { it.accept(body(byteArrayOf())) }
        }
        assertEmpty()
        assertThrows(IllegalArgumentException::class.java) { UploadLimits(fileBytes = 0) }
        assertThrows(UploadDecodingException::class.java) { decoder.multipartBody("multipart/form-data") }
        assertThrows(
            UploadDecodingException::class.java,
        ) { decoder.multipartBody("multipart/form-data; boundary=bad value") }
        assertEquals(
            FailureCategory.UNSUPPORTED_MEDIA_TYPE,
            assertThrows(UploadDecodingException::class.java) { decoder.multipartBody("application/json") }.category,
        )
    }

    @Test
    fun `file count rejection and cancellation during partial reading clean all owned storage`() {
        val wire =
            body(byteArrayOf(1))
                .toString(Charsets.UTF_8)
                .replace(
                    "\r\n--boundary--\r\n",
                    "\r\n--boundary\r\n" +
                        "Content-Disposition: form-data; name=\"attachment\"; " +
                        "filename=\"second\"\r\n\r\nx\r\n--boundary--\r\n",
                )
        val exceeded =
            assertThrows(UploadDecodingException::class.java) {
                decoder.multipartBody(TYPE, UploadLimits(fileCount = 1), directory).use { parser ->
                    parser.accept(wire.toByteArray())
                }
            }
        assertEquals(UploadLimit.FILE_COUNT, exceeded.limit)
        assertEmpty()
        assertThrows(CancellationException::class.java) {
            decoder.multipartBody(TYPE, temporaryDirectory = directory).use { parser ->
                parser.accept(body(byteArrayOf(1, 2)).dropLast(18).toByteArray())
                throw CancellationException("Reader cancelled")
            }
        }
        assertEmpty()
    }

    @Test
    fun `maximum-size quoted filenames parse with bounded memory without regex recursion`() {
        val filename = "x".repeat(7000)
        val wire =
            body(byteArrayOf())
                .toString(Charsets.UTF_8)
                .replace("../../private;name.txt", filename)
                .toByteArray()
        decoder.multipartBody(TYPE, temporaryDirectory = directory).use { parser ->
            parser.accept(wire)
            parser.finish().use { assertEquals(filename, it.files.single().filename) }
        }
        assertEmpty()
    }

    private fun body(data: ByteArray): ByteArray =
        (
            "--boundary\r\nContent-Disposition: form-data; name=\"title\"\r\n\r\n\u00e9<&\r\n" +
                "--boundary\r\nContent-Disposition: form-data; name=\"attachment\"; " +
                "filename=\"../../private;name.txt\"\r\n" +
                "Content-Type: application/octet-stream\r\n\r\n"
        ).toByteArray() +
            data + "\r\n--boundary--\r\n".toByteArray()

    @Test
    fun `cleanup failures preserve the application failure and are not silently discarded`() {
        val submission =
            decoder.multipartBody(TYPE, temporaryDirectory = directory).use { parser ->
                parser.accept(body(byteArrayOf(1)))
                parser.finish()
            }
        val root = Files.list(directory).use { it.findFirst().orElseThrow() }
        val obstruction = Files.createFile(root.resolve("unexpected"))
        val context =
            RequestContext(RequestMethod.POST, RequestTrace(RequestId.of("upload"), CorrelationId.of("upload")))
        val failure = IllegalStateException("Application failed")
        try {
            val thrown =
                assertThrows(IllegalStateException::class.java) {
                    runBlocking { PageRequest(submission, context).withUploadCleanup { throw failure } }
                }
            assertEquals(failure, thrown)
            assertTrue(thrown.suppressed.single() is java.io.IOException)
        } finally {
            Files.delete(obstruction)
            submission.close()
        }
        assertEmpty()
    }

    private fun assertEmpty() {
        Files.list(directory).use { assertEquals(0, it.count()) }
    }

    @Serializable
    private data class Command(
        val title: String,
    )

    private companion object {
        const val TYPE = "multipart/form-data; boundary=boundary"
    }
}

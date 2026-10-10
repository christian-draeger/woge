package dev.woge.host

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections

/** Upload allowances belong to one request. Bytes are not part of the patch protocol. */
public data class UploadLimits(
    public val requestBytes: Long = DEFAULT_UPLOAD_REQUEST_BYTES,
    public val fileBytes: Long = DEFAULT_UPLOAD_FILE_BYTES,
    public val temporaryBytes: Long = DEFAULT_UPLOAD_REQUEST_BYTES,
    public val fileCount: Int = 8,
    public val headerBytes: Int = 8192,
) {
    init {
        require(requestBytes > 0 && fileBytes > 0 && temporaryBytes > 0 && fileCount > 0 && headerBytes > 0) {
            "Upload limits must be positive"
        }
    }
}

public enum class UploadLimit {
    REQUEST_BYTES,
    FILE_BYTES,
    TEMPORARY_BYTES,
    FILE_COUNT,
    HEADER_BYTES,
}

/** Rejected upload diagnostics contain no filenames, field contents or temporary paths. */
public class UploadDecodingException(
    public val category: FailureCategory,
    public val limit: UploadLimit? = null,
    public val threshold: Long? = null,
) : RuntimeException("WOGE_UPLOAD_REJECTED: category=$category limit=$limit threshold=$threshold")

/** Untrusted metadata and request-scoped bytes. Never use [filename] as a storage path. */
public class UploadedFile internal constructor(
    public val field: String,
    public val filename: String,
    public val mediaType: String?,
    public val size: Long,
    private val path: Path,
    private val owner: UploadStorage,
) {
    /** The stream is always closed on return. Copy accepted bytes to application-owned storage here. */
    public fun <Result> read(block: (InputStream) -> Result): Result {
        owner.requireOpen()
        return Files.newInputStream(path).use(block)
    }

    override fun toString(): String = "UploadedFile(metadata=<untrusted>, size=$size)"
}

/** Text validation plus uploads, valid only while the application use case is executing. */
public class MultipartSubmission<Command : Any> internal constructor(
    public val form: FormSubmission<Command>,
    files: List<UploadedFile>,
    private val storage: UploadStorage,
) : AutoCloseable {
    public val files: List<UploadedFile> = Collections.unmodifiableList(files.toList())

    public fun files(field: String): List<UploadedFile> = files.filter { it.field == field }

    override fun close(): Unit = storage.close()

    override fun toString(): String = "MultipartSubmission(form=<redacted>, fileCount=${files.size})"
}

/** Hosts close multipart input when the use case returns or throws, before lazy response rendering. */
public suspend fun <Input : Any> PageRequest<Input>.withUploadCleanup(block: suspend () -> PageResult): PageResult =
    (input as? MultipartSubmission<*>).use {
        currentCoroutineContext().ensureActive()
        block()
    }

internal class UploadStorage(
    private val parent: Path?,
) : AutoCloseable {
    private var directory: Path? = null
    private val paths = mutableListOf<Path>()
    private var closed = false

    fun requireOpen() {
        check(!closed) { "Upload bytes are no longer available after request completion" }
    }

    fun create(): Path {
        requireOpen()
        val root =
            directory ?: (
                if (parent == null) {
                    Files.createTempDirectory("woge-upload-")
                } else {
                    Files.createTempDirectory(parent, "woge-upload-")
                }
            ).also { directory = it }
        return Files.createTempFile(root, "part-", ".tmp").also(paths::add)
    }

    override fun close() {
        if (closed) return
        var failure: java.io.IOException? = null
        for (path in paths + listOfNotNull(directory)) {
            try {
                Files.deleteIfExists(path)
            } catch (cause: java.io.IOException) {
                if (failure == null) failure = cause else failure.addSuppressed(cause)
            }
        }
        failure?.let { throw it }
        closed = true
    }
}

private const val DEFAULT_UPLOAD_FILE_BYTES = 8L * 1024 * 1024
private const val DEFAULT_UPLOAD_REQUEST_BYTES = 16L * 1024 * 1024

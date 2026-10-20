package dev.woge.tck

import dev.woge.host.DirectoryUploadTemporaryFiles
import dev.woge.host.UploadTemporaryFiles
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** In-memory test-fixture events; never attach these hooks to production routes. */
public object TckUploadDiagnostics {
    public const val HEADER: String = "X-Woge-Tck-Upload"

    private val requests = ConcurrentHashMap<String, UploadTimeline>()
    private val directories = ConcurrentHashMap<Path, String>()

    /** Only IDs registered by the local TCK can produce diagnostic events. */
    public fun record(
        requestId: String?,
        phase: String,
    ) {
        if (requestId != null) requests[requestId]?.record(requestId, phase)
    }

    internal fun register(
        directory: Path,
        timeline: UploadTimeline,
        case: String,
    ): String {
        val id = "${timeline.id}-$case"
        requests[id] = timeline
        directories[directory] = id
        record(id, "case.begin")
        return id
    }

    internal fun release(
        directory: Path,
        timeline: UploadTimeline,
    ) {
        directories.remove(directory)
        requests.entries.removeIf { it.value === timeline }
    }

    internal fun temporaryFiles(directory: Path): UploadTemporaryFiles {
        // Captured once for this request, including asynchronous cleanup after decoding returns.
        val id = directories[directory]
        val delegate = DirectoryUploadTemporaryFiles(directory)
        record(id, "files.store.created")
        return object : UploadTemporaryFiles {
            override fun create(): Path = delegate.create().also { record(id, "files.part.created") }

            override fun close() {
                record(id, "files.close.begin")
                delegate.close()
                record(id, "files.close.complete")
            }
        }
    }
}

internal class UploadTimeline(
    private val directory: Path,
) : AutoCloseable {
    val id: String = UUID.randomUUID().toString()
    private val started = System.nanoTime()
    private val events = mutableListOf<String>()
    private var printed = false

    fun begin(case: String): String = TckUploadDiagnostics.register(directory, this, case)

    @Synchronized
    fun record(
        requestId: String,
        phase: String,
    ) {
        if (events.size < MAX_UPLOAD_EVENTS) {
            val micros = (System.nanoTime() - started) / NANOS_PER_MICROSECOND
            events += "$micros us [$requestId] [${Thread.currentThread().name}] $phase"
        }
    }

    @Synchronized
    fun print() {
        if (!printed) {
            println("Multipart TCK timeline $id (at most $MAX_UPLOAD_EVENTS events):\n${events.joinToString("\n")}")
            printed = true
        }
    }

    override fun close() {
        if (System.getenv("WOGE_TCK_UPLOAD_DIAGNOSTICS") == "true") print()
        TckUploadDiagnostics.release(directory, this)
    }
}

private const val MAX_UPLOAD_EVENTS = 2048
private const val NANOS_PER_MICROSECOND = 1000

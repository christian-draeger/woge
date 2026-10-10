package dev.woge.spring.webflux

import dev.woge.host.FormDecoder
import dev.woge.host.MultipartSubmission
import dev.woge.host.UploadLimits
import dev.woge.host.multipartBody
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.withContext
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.web.reactive.function.BodyExtractors
import java.nio.file.Path

/** Uses bounded raw buffers and offloads temporary-file writes from the Reactor event loop. */
public fun <Command : Any> FormDecoder<Command>.webFluxMultipart(
    limits: UploadLimits = UploadLimits(),
    temporaryDirectory: Path? = null,
): WebFluxPageInput<MultipartSubmission<Command>> =
    WebFluxPageInput { request ->
        var completed: MultipartSubmission<Command>? = null
        try {
            withContext(Dispatchers.IO) {
                multipartBody(request.headers().firstHeader("Content-Type"), limits, temporaryDirectory).use { body ->
                    val bytes = ByteArray(UPLOAD_READ_BYTES)
                    request
                        .body(BodyExtractors.toDataBuffers())
                        .doOnDiscard(DataBuffer::class.java, DataBufferUtils::release)
                        .asFlow()
                        .buffer(0)
                        .collect { buffer ->
                            try {
                                while (buffer.readableByteCount() > 0) {
                                    val count = minOf(buffer.readableByteCount(), bytes.size)
                                    buffer.read(bytes, 0, count)
                                    body.accept(bytes, length = count)
                                }
                            } finally {
                                DataBufferUtils.release(buffer)
                            }
                        }
                    body.finish().also { completed = it }
                }
            }
        } catch (cancelled: CancellationException) {
            completed?.close()
            throw cancelled
        }
    }

private const val UPLOAD_READ_BYTES = 4096

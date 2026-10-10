package dev.woge.ktor

import dev.woge.host.FormDecoder
import dev.woge.host.MultipartSubmission
import dev.woge.host.UploadLimits
import dev.woge.host.multipartBody
import io.ktor.http.HttpHeaders
import io.ktor.server.request.receiveChannel
import io.ktor.utils.io.readAvailable
import java.nio.file.Path

/** Reads native uploads without Ktor's full-body multipart buffering. Security runs before decoding. */
public fun <Command : Any> FormDecoder<Command>.ktorMultipart(
    limits: UploadLimits = UploadLimits(),
    temporaryDirectory: Path? = null,
): KtorPageInput<MultipartSubmission<Command>> =
    KtorPageInput { call ->
        val channel = call.receiveChannel()
        try {
            multipartBody(call.request.headers[HttpHeaders.ContentType], limits, temporaryDirectory).use { body ->
                val bytes = ByteArray(UPLOAD_READ_BYTES)
                var count = channel.readAvailable(bytes)
                while (count != -1) {
                    body.accept(bytes, length = count)
                    count = channel.readAvailable(bytes)
                }
                body.finish()
            }
        } finally {
            channel.cancel(null)
        }
    }

private const val UPLOAD_READ_BYTES = 4096

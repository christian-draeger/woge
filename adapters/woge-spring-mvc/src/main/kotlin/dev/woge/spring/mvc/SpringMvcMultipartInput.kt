package dev.woge.spring.mvc

import dev.woge.host.FormDecoder
import dev.woge.host.MultipartSubmission
import dev.woge.host.UploadLimits
import dev.woge.host.multipartBody
import org.springframework.web.multipart.MultipartHttpServletRequest
import java.nio.file.Path

/** Reads raw native uploads with Woge limits rather than Servlet container multipart configuration. */
public fun <Command : Any> FormDecoder<Command>.springMvcMultipart(
    limits: UploadLimits = UploadLimits(),
    temporaryDirectory: Path? = null,
): SpringMvcPageInput<MultipartSubmission<Command>> =
    SpringMvcPageInput { request ->
        check(request !is MultipartHttpServletRequest) {
            "Woge uploads need WogeMultipartResolver for this action path; Spring already consumed the multipart body"
        }
        multipartBody(request.contentType, limits, temporaryDirectory).use { body ->
            val bytes = ByteArray(UPLOAD_READ_BYTES)
            val stream = request.inputStream
            var count = stream.read(bytes)
            while (count != -1) {
                body.accept(bytes, length = count)
                count = stream.read(bytes)
            }
            body.finish()
        }
    }

private const val UPLOAD_READ_BYTES = 4096

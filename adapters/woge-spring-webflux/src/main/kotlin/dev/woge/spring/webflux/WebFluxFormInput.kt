package dev.woge.spring.webflux

import dev.woge.host.FormBody
import dev.woge.host.FormDecoder
import dev.woge.host.FormSubmission
import dev.woge.host.getOrThrow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.reactive.asFlow
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.web.reactive.function.BodyExtractors

/** Consumes and releases body buffers incrementally; rejection cancels the upstream body. */
public fun <Command : Any> FormDecoder<Command>.webFluxInput(): WebFluxPageInput<Command> =
    webFluxFormInput { decode(it).getOrThrow() }

/** Keeps bounded submitted text available to a native validation renderer. */
public fun <Command : Any> FormDecoder<Command>.webFluxSubmission(): WebFluxPageInput<FormSubmission<Command>> =
    webFluxFormInput(::submission)

private fun <Command : Any, Input : Any> FormDecoder<Command>.webFluxFormInput(
    read: (FormBody) -> Input,
): WebFluxPageInput<Input> =
    WebFluxPageInput { request ->
        requireContentType(request.headers().firstHeader("Content-Type"))
        val body = body()
        val bytes = ByteArray(FORM_READ_BYTES)
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
                        if (!body.accept(bytes, length = count)) decode(body).getOrThrow()
                    }
                } finally {
                    DataBufferUtils.release(buffer)
                }
            }
        read(body)
    }

private const val FORM_READ_BYTES = 4096

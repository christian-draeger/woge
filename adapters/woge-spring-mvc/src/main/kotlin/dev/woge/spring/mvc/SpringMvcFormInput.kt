package dev.woge.spring.mvc

import dev.woge.host.FormDecoder
import dev.woge.host.getOrThrow

/** Reads only the bounded URL-encoded body, never merged Servlet query/form parameters. */
public fun <Command : Any> FormDecoder<Command>.springMvcInput(): SpringMvcPageInput<Command> =
    SpringMvcPageInput { request ->
        requireContentType(request.contentType)
        val body = body()
        val buffer = ByteArray(FORM_READ_BYTES)
        val stream = request.inputStream
        var count = stream.read(buffer)
        while (count != -1) {
            if (!body.accept(buffer, length = count)) break
            count = stream.read(buffer)
        }
        decode(body).getOrThrow()
    }

private const val FORM_READ_BYTES = 4096

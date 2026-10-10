package dev.woge.ktor

import dev.woge.host.FormBody
import dev.woge.host.FormDecoder
import dev.woge.host.FormSubmission
import dev.woge.host.getOrThrow
import io.ktor.http.HttpHeaders
import io.ktor.server.request.receiveChannel
import io.ktor.utils.io.readAvailable

/** Reads a native form's body without Ktor's full-body parameter buffering. */
public fun <Command : Any> FormDecoder<Command>.ktorInput(): KtorPageInput<Command> =
    ktorFormInput { decode(it).getOrThrow() }

/** Keeps bounded submitted text available to a native validation renderer. */
public fun <Command : Any> FormDecoder<Command>.ktorSubmission(): KtorPageInput<FormSubmission<Command>> =
    ktorFormInput(::submission)

private fun <Command : Any, Input : Any> FormDecoder<Command>.ktorFormInput(
    read: (FormBody) -> Input,
): KtorPageInput<Input> =
    KtorPageInput { call ->
        requireContentType(call.request.headers[HttpHeaders.ContentType])
        val body = body()
        val bytes = ByteArray(FORM_READ_BYTES)
        val channel = call.receiveChannel()
        var count = channel.readAvailable(bytes)
        while (count != -1) {
            if (!body.accept(bytes, length = count)) {
                channel.cancel(null)
                break
            }
            count = channel.readAvailable(bytes)
        }
        read(body)
    }

private const val FORM_READ_BYTES = 4096

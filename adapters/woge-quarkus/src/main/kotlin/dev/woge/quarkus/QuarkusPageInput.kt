package dev.woge.quarkus

import dev.woge.host.FormBody
import dev.woge.host.FormDecoder
import dev.woge.host.FormSubmission
import dev.woge.host.MultipartSubmission
import dev.woge.host.PageRoute
import dev.woge.host.RouteParameters
import dev.woge.host.UploadPolicy
import dev.woge.host.getOrThrow
import dev.woge.host.multipartBody
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.coroutines.toReceiveChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Decodes input while the Quarkus request remains owned by its router context. */
public fun interface QuarkusPageInput<Input : Any> {
    public suspend fun decode(call: RoutingContext): Input
}

/** Reads actual path and query parameters from the matched Vert.x route. */
public fun <Input : Any> PageRoute<Input>.quarkusInput(): QuarkusPageInput<Input> =
    QuarkusPageInput { call ->
        decode(
            object : RouteParameters {
                override fun path(name: String): String? = call.pathParam(name)

                override fun query(name: String): String? = call.queryParam(name).singleOrNull()
            },
        )
    }

/** Decodes a bounded native URL-encoded command. */
public fun <Command : Any> FormDecoder<Command>.quarkusInput(): QuarkusPageInput<Command> =
    urlEncoded { decode(it).getOrThrow() }

/** Keeps bounded submitted values for a portable validation renderer. */
public fun <Command : Any> FormDecoder<Command>.quarkusSubmission(): QuarkusPageInput<FormSubmission<Command>> =
    urlEncoded(::submission)

private fun <Command : Any, Input : Any> FormDecoder<Command>.urlEncoded(
    finish: (FormBody) -> Input,
): QuarkusPageInput<Input> =
    QuarkusPageInput { call ->
        requireContentType(call.headerValues("Content-Type"))
        val body = body()
        call.readBody { body.accept(it.bytes) }
        finish(body)
    }

/** Streams owned parts to the portable multipart reader with disk work on the IO dispatcher. */
@Suppress("TooGenericExceptionCaught")
public fun <Command : Any> FormDecoder<Command>.quarkusMultipart(
    policy: UploadPolicy,
): QuarkusPageInput<MultipartSubmission<Command>> =
    QuarkusPageInput { call ->
        val headers = call.headerValues("Content-Type")
        val body = withContext(Dispatchers.IO) { multipartBody(headers, policy) }
        var submission: MultipartSubmission<Command>? = null
        try {
            call.readBody { buffer ->
                val bytes = buffer.bytes
                withContext(Dispatchers.IO) { body.accept(bytes) }
                true
            }
            withContext(Dispatchers.IO) {
                body.finish().also { submission = it }
            }
        } catch (failure: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) { submission?.close() }
            throw failure
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { body.close() }
        }
    }

private suspend fun RoutingContext.readBody(onChunk: suspend (Buffer) -> Boolean) {
    val request = request()
    if (request.isEnded) return
    val input = request.toReceiveChannel(checkNotNull(Vertx.currentContext()))
    try {
        for (buffer in input) {
            if (!onChunk(buffer)) break
        }
    } finally {
        input.cancel()
        if (!request.isEnded) request.handler(null).resume()
    }
}

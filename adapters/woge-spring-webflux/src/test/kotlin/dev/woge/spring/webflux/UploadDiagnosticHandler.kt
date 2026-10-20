package dev.woge.spring.webflux

import dev.woge.tck.TckUploadDiagnostics
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import org.reactivestreams.Publisher
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.server.reactive.HttpHandler
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.http.server.reactive.ServerHttpRequestDecorator
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.http.server.reactive.ServerHttpResponseDecorator
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.netty.DisposableServer
import reactor.netty.http.server.HttpServer

internal fun uploadDiagnosticServer(handler: HttpHandler): DisposableServer {
    val http = ReactorHttpHandlerAdapter(handler.withUploadDiagnostics())
    return HttpServer
        .create()
        .host("127.0.0.1")
        .port(0)
        .handle { request, response ->
            val id = request.requestHeaders().get(TckUploadDiagnostics.HEADER)
            request.withConnection {
                val channel = it.channel()
                TckUploadDiagnostics.record(
                    id,
                    "netty.request connection=${channel.id().asShortText()} transport=${channel.javaClass.simpleName}",
                )
            }
            http.apply(request, response).doFinally { TckUploadDiagnostics.record(id, "netty.handler.end signal=$it") }
        }.bindNow()
}

@Suppress("TooGenericExceptionCaught")
internal fun <Input : Any> WebFluxPageInput<Input>.withUploadDiagnostics(): WebFluxPageInput<Input> =
    WebFluxPageInput { request ->
        val id = request.headers().firstHeader(TckUploadDiagnostics.HEADER)
        val job = currentCoroutineContext()[Job]
        TckUploadDiagnostics.record(id, "decoder.enter coroutineActive=${job?.isActive}")
        try {
            decode(request).also { TckUploadDiagnostics.record(id, "decoder.complete") }
        } catch (failure: Exception) {
            TckUploadDiagnostics.record(
                id,
                "decoder.error type=${failure.javaClass.simpleName} coroutineActive=${job?.isActive}",
            )
            throw failure
        }
    }

/** Observes the real request publisher without consuming, retaining or logging its buffers. */
internal fun HttpHandler.withUploadDiagnostics(): HttpHandler =
    HttpHandler { request, response ->
        val id = request.headers.getFirst(TckUploadDiagnostics.HEADER)
        if (id == null) {
            handle(request, response)
        } else {
            TckUploadDiagnostics.record(id, "spring.handler.enter")
            handle(diagnosticRequest(request, id), diagnosticResponse(response, id))
                .doFinally { TckUploadDiagnostics.record(id, "spring.handler.end signal=$it") }
        }
    }

private fun diagnosticRequest(
    request: ServerHttpRequest,
    id: String?,
): ServerHttpRequest =
    object : ServerHttpRequestDecorator(request) {
        override fun getBody(): Flux<DataBuffer> =
            super
                .getBody()
                .doOnSubscribe { TckUploadDiagnostics.record(id, "server.body.subscribe") }
                .doOnRequest { TckUploadDiagnostics.record(id, "server.body.demand count=$it") }
                .doOnNext { TckUploadDiagnostics.record(id, "server.body.receive bytes=${it.readableByteCount()}") }
                .doOnComplete { TckUploadDiagnostics.record(id, "server.body.complete") }
                .doOnError { TckUploadDiagnostics.record(id, "server.body.error type=${it.javaClass.simpleName}") }
                .doOnCancel { TckUploadDiagnostics.record(id, "server.body.cancel") }
    }

private fun diagnosticResponse(
    response: ServerHttpResponse,
    id: String?,
): ServerHttpResponse {
    response.beforeCommit {
        TckUploadDiagnostics.record(id, "server.response.commit status=${response.statusCode?.value()}")
        Mono.empty()
    }
    return object : ServerHttpResponseDecorator(response) {
        override fun writeWith(body: Publisher<out DataBuffer>): Mono<Void> {
            TckUploadDiagnostics.record(id, "server.response.write")
            return super.writeWith(body).doFinally { TckUploadDiagnostics.record(id, "server.response.end signal=$it") }
        }

        override fun writeAndFlushWith(body: Publisher<out Publisher<out DataBuffer>>): Mono<Void> {
            TckUploadDiagnostics.record(id, "server.response.write.flush")
            return super
                .writeAndFlushWith(body)
                .doFinally { TckUploadDiagnostics.record(id, "server.response.end signal=$it") }
        }

        override fun setComplete(): Mono<Void> {
            TckUploadDiagnostics.record(id, "server.response.empty")
            return super.setComplete().doFinally { TckUploadDiagnostics.record(id, "server.response.end signal=$it") }
        }
    }
}

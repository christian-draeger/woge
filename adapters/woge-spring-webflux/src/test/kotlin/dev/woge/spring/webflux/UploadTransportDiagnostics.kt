package dev.woge.spring.webflux

import dev.woge.tck.TckUploadDiagnostics
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPipeline
import io.netty.channel.ChannelPromise
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpUtil
import io.netty.handler.codec.http.LastHttpContent
import reactor.netty.NettyPipeline
import reactor.netty.http.server.HttpServer
import java.net.InetSocketAddress

internal fun HttpServer.withUploadTransportDiagnostics(): HttpServer =
    if (System.getenv("WOGE_TCK_UPLOAD_TRANSPORT_DIAGNOSTICS") == "true") {
        doOnChannelInit { _, channel, _ -> UploadTransportTimeline(channel.pipeline()).install() }
    } else {
        this
    }

/** Test-only boundaries on either side of HTTP decoding. Publishes metadata without retaining messages. */
private class UploadTransportTimeline(
    private val pipeline: ChannelPipeline,
) {
    private var lastRequestId: String? = null
    private var framedBytes = 0L

    fun install() {
        pipeline.addBefore(NettyPipeline.HttpCodec, "wogeUploadSocket", socketEvents())
        pipeline.addBefore(NettyPipeline.HttpTrafficHandler, "wogeUploadFrames", framingEvents())
    }

    private fun socketEvents(): ChannelDuplexHandler =
        object : ChannelDuplexHandler() {
            // Before a new header has been decoded, associate connection events with the preceding request.
            override fun read(ctx: ChannelHandlerContext) {
                record("netty.socket.read.after autoRead=${ctx.channel().config().isAutoRead}")
                super.read(ctx)
            }

            override fun channelRead(
                ctx: ChannelHandlerContext,
                msg: Any,
            ) {
                if (msg is ByteBuf) record("netty.socket.bytes.after count=${msg.readableBytes()}")
                super.channelRead(ctx, msg)
            }

            override fun channelInactive(ctx: ChannelHandlerContext) {
                record("netty.socket.inactive.after")
                super.channelInactive(ctx)
            }
        }

    private fun framingEvents(): ChannelDuplexHandler =
        object : ChannelDuplexHandler() {
            override fun channelRead(
                ctx: ChannelHandlerContext,
                msg: Any,
            ) {
                if (msg is HttpRequest) {
                    lastRequestId = msg.headers().get(TckUploadDiagnostics.HEADER)
                    framedBytes = 0
                    val peerPort = (ctx.channel().remoteAddress() as InetSocketAddress).port
                    val localPort = (ctx.channel().localAddress() as InetSocketAddress).port
                    record("netty.frame.request peerPort=$peerPort localPort=$localPort")
                }
                if (msg is HttpContent) framedBytes += msg.content().readableBytes()
                if (msg is LastHttpContent) record("netty.frame.request.end bytes=$framedBytes")
                super.channelRead(ctx, msg)
            }

            override fun write(
                ctx: ChannelHandlerContext,
                msg: Any,
                promise: ChannelPromise,
            ) {
                if (msg is HttpResponse) {
                    record("netty.frame.response status=${msg.status().code()} keepAlive=${HttpUtil.isKeepAlive(msg)}")
                }
                super.write(ctx, msg, promise)
            }
        }

    private fun record(phase: String) {
        val id = lastRequestId ?: return
        TckUploadDiagnostics.record(id, "$phase connection=${pipeline.channel().id().asShortText()}")
    }
}

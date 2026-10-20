package dev.woge.tck

import java.net.http.HttpRequest
import java.nio.ByteBuffer
import java.util.concurrent.Flow

internal fun HttpRequest.BodyPublisher.withUploadDiagnostics(requestId: String?): HttpRequest.BodyPublisher {
    if (requestId == null) return this
    val delegate = this
    return object : HttpRequest.BodyPublisher {
        override fun contentLength(): Long = delegate.contentLength()

        override fun subscribe(subscriber: Flow.Subscriber<in ByteBuffer>) {
            TckUploadDiagnostics.record(requestId, "client.body.subscribe length=${contentLength()}")
            delegate.subscribe(
                object : Flow.Subscriber<ByteBuffer> {
                    override fun onSubscribe(subscription: Flow.Subscription) {
                        subscriber.onSubscribe(subscription)
                    }

                    override fun onNext(item: ByteBuffer) {
                        TckUploadDiagnostics.record(requestId, "client.body.publish bytes=${item.remaining()}")
                        subscriber.onNext(item)
                    }

                    override fun onError(throwable: Throwable) {
                        TckUploadDiagnostics.record(
                            requestId,
                            "client.body.error type=${throwable.javaClass.simpleName}",
                        )
                        subscriber.onError(throwable)
                    }

                    override fun onComplete() {
                        TckUploadDiagnostics.record(requestId, "client.body.complete")
                        subscriber.onComplete()
                    }
                },
            )
        }
    }
}

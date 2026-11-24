package example.micronaut;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import jakarta.annotation.PreDestroy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Controller
@Requires(property = "woge.stream-proof", value = "true")
public final class StreamProofController {
    static final String PATH = "/stream-proof";
    static final byte[] FIRST = {0, 60, (byte) 255, 10};
    static final byte[] LAST = {5, (byte) 128, 0};

    final AtomicInteger subscriptions = new AtomicInteger();
    final CountDownLatch workStarted = new CountDownLatch(1);
    final CountDownLatch releaseWork = new CountDownLatch(1);
    final CountDownLatch cleanup = new CountDownLatch(1);
    final CountDownLatch terminated = new CountDownLatch(1);
    final AtomicReference<String> workerThread = new AtomicReference<>();
    final AtomicReference<SignalType> terminalSignal = new AtomicReference<>();
    private final Scheduler workers = Schedulers.newBoundedElastic(1, 1, "woge-stream-proof");

    @Get(value = PATH, produces = MediaType.APPLICATION_OCTET_STREAM)
    @Secured(SecurityRule.IS_ANONYMOUS)
    public HttpResponse<Flux<byte[]>> stream() {
        var bytes = Flux.defer(() -> {
            subscriptions.incrementAndGet();
            return Flux.concat(Mono.just(FIRST), Mono.fromCallable(this::produceLast).subscribeOn(workers));
        }).doFinally(signal -> {
            terminalSignal.set(signal);
            terminated.countDown();
        });
        return HttpResponse.ok(bytes).contentType(MediaType.APPLICATION_OCTET_STREAM_TYPE);
    }

    private byte[] produceLast() throws InterruptedException {
        workerThread.set(Thread.currentThread().getName());
        workStarted.countDown();
        try {
            if (!releaseWork.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Test did not release or cancel the pending producer");
            }
            return LAST;
        } finally {
            cleanup.countDown();
        }
    }

    @PreDestroy
    void closeWorkers() {
        workers.dispose();
    }
}

package example.micronaut;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.netty.channel.EventLoopGroupRegistry;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.SignalType;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StreamProofHttpTest {
    @Test
    void flushesRawBytesBeforeDeferredWorkAndLeavesTheOnlyEventLoopResponsive() throws Exception {
        try (var server = startServer()) {
            var context = server.getApplicationContext();
            var eventLoops = context.getBean(EventLoopGroupRegistry.class).getDefaultEventLoopGroup();
            assertEquals(1, StreamSupport.stream(eventLoops.spliterator(), false).count());
            var proof = context.getBean(StreamProofController.class);
            proof.stream();
            assertEquals(0, proof.subscriptions.get(), "Constructing the response must leave the producer cold");

            try (var response = ChunkedResponse.get(server, StreamProofController.PATH, Map.of())) {
                assertFirstChunk(response);
                assertTrue(proof.workStarted.await(5, TimeUnit.SECONDS), "Deferred work did not start");
                assertTrue(proof.workerThread.get().startsWith("woge-stream-proof-"));
                assertEquals(1, proof.releaseWork.getCount(), "Deferred work must still be held");

                try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()) {
                    var request = HttpRequest.newBuilder(server.getURL().toURI())
                            .timeout(Duration.ofSeconds(3)).build();
                    assertEquals(200, client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode());
                }

                proof.releaseWork.countDown();
                assertArrayEquals(StreamProofController.LAST, response.readChunk());
                assertArrayEquals(new byte[0], response.readChunk());
                assertTrue(proof.terminated.await(5, TimeUnit.SECONDS));
                assertEquals(SignalType.ON_COMPLETE, proof.terminalSignal.get());
                assertEquals(0, proof.cleanup.getCount());
                assertEquals(1, proof.subscriptions.get());
            } finally {
                proof.releaseWork.countDown();
            }
        }
    }

    @Test
    void cancelsAndCleansUpDeferredWorkWhenThePeerDropsTheConnection() throws Exception {
        try (var server = startServer()) {
            var proof = server.getApplicationContext().getBean(StreamProofController.class);
            try {
                try (var response = ChunkedResponse.get(server, StreamProofController.PATH, Map.of())) {
                    assertFirstChunk(response);
                    assertTrue(proof.workStarted.await(5, TimeUnit.SECONDS), "Deferred work did not start");
                    response.resetOnClose();
                }

                assertTrue(proof.terminated.await(5, TimeUnit.SECONDS), "Netty did not cancel the publisher");
                assertEquals(SignalType.CANCEL, proof.terminalSignal.get());
                assertTrue(proof.cleanup.await(5, TimeUnit.SECONDS), "Deferred worker did not exit after cancellation");
                assertEquals(1, proof.releaseWork.getCount(), "Cleanup must happen before the test releases work");
            } finally {
                proof.releaseWork.countDown();
            }
        }
    }

    @Test
    void passiveCloseCancelsWithoutResettingTheSocketOrReleasingTheProducer() throws Exception {
        try (var server = startServer()) {
            var proof = server.getApplicationContext().getBean(StreamProofController.class);
            try {
                try (var response = ChunkedResponse.get(server, StreamProofController.PATH, Map.of())) {
                    assertFirstChunk(response);
                    assertTrue(proof.workStarted.await(5, TimeUnit.SECONDS));
                }
                assertTrue(proof.terminated.await(5, TimeUnit.SECONDS));
                assertEquals(SignalType.CANCEL, proof.terminalSignal.get());
                assertTrue(proof.cleanup.await(5, TimeUnit.SECONDS));
                assertEquals(1, proof.releaseWork.getCount());
            } finally {
                proof.releaseWork.countDown();
            }
        }
    }

    @Test
    void slowPeerBoundsDemandAndARealSocketWriteFailureCancelsTheResponse() throws Exception {
        try (var server = startServer()) {
            var proof = server.getApplicationContext().getBean(StreamProofController.class);
            try (var response = ChunkedResponse.get(server, StreamProofController.SLOW_PATH, Map.of(), 4096)) {
                assertArrayEquals(StreamProofController.SLOW_CHUNK, response.readChunk());
                int emitted = awaitBackpressure(proof);
                // The fixture offers 32 MiB. With a deliberately small socket and write buffer,
                // delivery must stop far below that before the peer drains or closes its socket.
                assertTrue(emitted < 256, "Unexpected buffered chunk count: " + emitted);
                try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()) {
                    var request = HttpRequest.newBuilder(server.getURL().toURI())
                            .timeout(Duration.ofSeconds(3)).build();
                    assertEquals(200, client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode());
                }
                assertEquals(emitted, proof.slowProduced.get());
                System.out.printf("Paused peer held production at %d of %d chunks.%n", emitted, StreamProofController.SLOW_CHUNKS);
                response.resetOnClose();
            }
            assertTrue(proof.slowWriteFailed.await(5, TimeUnit.SECONDS), "Expected a failed Netty write promise");
            assertTrue(proof.slowTerminated.await(5, TimeUnit.SECONDS));
            assertEquals(SignalType.CANCEL, proof.slowTerminalSignal.get());
        }
    }

    private static int awaitBackpressure(StreamProofController proof) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            int before = proof.slowProduced.get();
            Thread.sleep(200);
            if (!proof.slowChannel.get().isWritable() && proof.slowProduced.get() == before) return before;
        }
        return fail("The paused peer did not stall the Netty channel and producer");
    }

    private static EmbeddedServer startServer() {
        return ApplicationContext.run(EmbeddedServer.class, Map.of(
                "micronaut.server.port", -1,
                "micronaut.netty.event-loops.default.num-threads", 1,
                "woge.stream-proof", true
        ));
    }

    private static void assertFirstChunk(ChunkedResponse response) throws Exception {
        assertEquals("HTTP/1.1 200 OK", response.status);
        assertEquals("application/octet-stream", response.headers.get("content-type"));
        assertArrayEquals(StreamProofController.FIRST, response.readChunk());
    }
}

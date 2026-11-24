package example.micronaut;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.netty.channel.EventLoopGroupRegistry;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.SignalType;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

            try (var socket = connect(server)) {
                var response = new BufferedInputStream(socket.getInputStream());
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
                assertArrayEquals(StreamProofController.LAST, readChunk(response));
                assertArrayEquals(new byte[0], readChunk(response));
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
                try (var socket = connect(server)) {
                    var response = new BufferedInputStream(socket.getInputStream());
                    assertFirstChunk(response);
                    assertTrue(proof.workStarted.await(5, TimeUnit.SECONDS), "Deferred work did not start");
                    socket.setSoLinger(true, 0);
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

    private static EmbeddedServer startServer() {
        return ApplicationContext.run(EmbeddedServer.class, Map.of(
                "micronaut.server.port", -1,
                "micronaut.netty.event-loops.default.num-threads", 1,
                "woge.stream-proof", true
        ));
    }

    private static Socket connect(EmbeddedServer server) throws IOException {
        var socket = new Socket();
        socket.connect(new InetSocketAddress("127.0.0.1", server.getPort()), 5_000);
        socket.setSoTimeout(5_000);
        var request = "GET " + StreamProofController.PATH + " HTTP/1.1\r\n"
                + "Host: 127.0.0.1:" + server.getPort() + "\r\n\r\n";
        socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
        return socket;
    }

    private static void assertFirstChunk(BufferedInputStream response) throws IOException {
        assertEquals("HTTP/1.1 200 OK", readLine(response));
        Map<String, String> headers = new LinkedHashMap<>();
        for (String line = readLine(response); !line.isEmpty(); line = readLine(response)) {
            int separator = line.indexOf(':');
            assertTrue(separator > 0, "Expected an HTTP header");
            headers.put(line.substring(0, separator).toLowerCase(Locale.ROOT), line.substring(separator + 1).trim());
        }
        assertEquals("chunked", headers.get("transfer-encoding"));
        assertEquals("application/octet-stream", headers.get("content-type"));
        assertArrayEquals(StreamProofController.FIRST, readChunk(response));
    }

    private static byte[] readChunk(BufferedInputStream response) throws IOException {
        int length = Integer.parseInt(readLine(response).split(";", 2)[0], 16);
        assertTrue(length >= 0 && length <= 16, "Unexpected chunk size: " + length);
        byte[] bytes = response.readNBytes(length);
        assertEquals(length, bytes.length);
        assertEquals("", readLine(response));
        return bytes;
    }

    private static String readLine(BufferedInputStream response) throws IOException {
        var bytes = new ByteArrayOutputStream();
        for (int value; (value = response.read()) != '\r'; ) {
            if (value == -1 || bytes.size() >= 4096) {
                throw new IOException("Unterminated or oversized HTTP line");
            }
            bytes.write(value);
        }
        assertEquals('\n', response.read());
        return bytes.toString(StandardCharsets.US_ASCII);
    }
}

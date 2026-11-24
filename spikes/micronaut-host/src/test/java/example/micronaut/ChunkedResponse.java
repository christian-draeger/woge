package example.micronaut;

import io.micronaut.runtime.server.EmbeddedServer;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ChunkedResponse implements AutoCloseable {
    private final Socket socket;
    private final BufferedInputStream input;
    final String status;
    final Map<String, String> headers = new LinkedHashMap<>();

    static ChunkedResponse get(EmbeddedServer server, String path, Map<String, String> headers) throws IOException {
        var socket = new Socket();
        try {
            socket.connect(new InetSocketAddress("127.0.0.1", server.getPort()), 5_000);
            socket.setSoTimeout(5_000);
            var request = new StringBuilder("GET ").append(path)
                    .append(" HTTP/1.1\r\nHost: 127.0.0.1:").append(server.getPort()).append("\r\n");
            headers.forEach((name, value) -> request.append(name).append(": ").append(value).append("\r\n"));
            request.append("\r\n");
            socket.getOutputStream().write(request.toString().getBytes(StandardCharsets.US_ASCII));
            return new ChunkedResponse(socket);
        } catch (IOException | RuntimeException | AssertionError failure) {
            socket.close();
            throw failure;
        }
    }

    private ChunkedResponse(Socket socket) throws IOException {
        this.socket = socket;
        input = new BufferedInputStream(socket.getInputStream());
        status = readLine();
        for (String line = readLine(); !line.isEmpty(); line = readLine()) {
            int separator = line.indexOf(':');
            assertTrue(separator > 0, "Expected an HTTP header");
            headers.put(line.substring(0, separator).toLowerCase(Locale.ROOT), line.substring(separator + 1).trim());
        }
        assertEquals("chunked", headers.get("transfer-encoding"));
    }

    byte[] readChunk() throws IOException {
        int length = Integer.parseInt(readLine().split(";", 2)[0], 16);
        assertTrue(length >= 0 && length <= 8192, "Unexpected chunk size: " + length);
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) throw new EOFException("Incomplete HTTP chunk");
        assertEquals("", readLine());
        return bytes;
    }

    void resetOnClose() throws IOException {
        socket.setSoLinger(true, 0);
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

    private String readLine() throws IOException {
        var bytes = new ByteArrayOutputStream();
        for (int value; (value = input.read()) != '\r'; ) {
            if (value == -1) throw new EOFException("Incomplete HTTP line");
            if (bytes.size() >= 4096) throw new IOException("Oversized HTTP line");
            bytes.write(value);
        }
        assertEquals('\n', input.read());
        return bytes.toString(StandardCharsets.US_ASCII);
    }
}

package example.micronaut;

import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TodoHttpTest {
    @Test
    void servesThePortableTodoPageAndTypedFilter() throws Exception {
        try (var server = ApplicationContext.run(EmbeddedServer.class, Map.of("micronaut.server.port", -1));
             var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            URI origin = server.getURL().toURI();
            var home = send(client, origin, "/", "GET");

            assertEquals(200, home.statusCode());
            var contentType = home.headers().firstValue("content-type").orElseThrow();
            assertEquals("text/html; charset=utf-8", contentType.toLowerCase(Locale.ROOT));
            assertTrue(home.body().contains("<h1>Todos</h1>"));
            assertTrue(home.body().contains("href=\"/?filter=completed\""));

            var filtered = send(client, origin, "/?filter=completed", "GET");
            assertEquals(200, filtered.statusCode());
            assertTrue(filtered.body().contains("href=\"/?filter=completed\" aria-current=\"page\""));

            assertEquals(400, send(client, origin, "/?filter=invalid", "GET").statusCode());
            assertEquals(200, send(client, origin, "/todo.css", "GET").statusCode());
            assertEquals(200, send(client, origin, "/assets/woge/index.js", "GET").statusCode());

            var head = send(client, origin, "/", "HEAD");
            assertEquals(200, head.statusCode());
            assertEquals(home.headers().firstValue("content-type"), head.headers().firstValue("content-type"));
            assertEquals("", head.body());
        }
    }

    private HttpResponse<String> send(HttpClient client, URI origin, String path, String method) throws Exception {
        var request = HttpRequest.newBuilder(origin.resolve(path))
                .timeout(Duration.ofSeconds(5))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}

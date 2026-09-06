# Run a Woge page with Spring MVC

Woge's MVC adapter keeps the Servlet stack at the edge. Your page and components remain ordinary
Kotlin and semantic HTML; the route still looks like a normal URL mapping.

## Add the MVC dependencies

Use the neutral Boot starter with exactly one matching server stack:

```kotlin
dependencies {
    implementation("dev.woge:woge-spring-boot-starter:<version>")
    implementation("dev.woge:woge-spring-mvc:<version>")
    implementation("org.springframework.boot:spring-boot-starter-web")
}
```

With only MVC on the classpath, `woge.adapter=auto` selects it. Setting `woge.adapter=mvc` makes that
choice visible and gives an actionable startup error if the dependencies disagree.

## Map normal URLs

Boot supplies `WogeSpringMvcHandlers`. Register its handlers with Spring MVC's familiar URL mapping:

```kotlin
@Bean
fun projectRoutes(
    projectPage: ProjectPage,
    handlers: WogeSpringMvcHandlers,
): SimpleUrlHandlerMapping {
    val input = SpringMvcPageInput<ProjectInput> { request ->
        ProjectInput(request.pathVariable("project"))
    }

    return SimpleUrlHandlerMapping(
        mapOf(
            "/projects/{project}" to handlers.page(projectPage, input),
            "/projects/{project}/woge-patches" to handlers.deferred(projectPage, input),
        ),
        0,
    )
}
```

The application owns paths and input decoding. Page handlers accept GET and HEAD; deferred handlers
accept GET and return an ordinary 405 with `Allow` for other methods. The adapter owns Servlet async
mode, response metadata, body streaming and cancellation. There is no application controller that
must repeat Woge transport code.

## Understand the execution model

Input and request context are snapshotted on the original Servlet request thread. Woge then starts
Servlet asynchronous processing, releases that thread and runs the page coroutine on
`Dispatchers.IO` by default. Suspended work consumes no thread. Rendering and every Servlet
`OutputStream` write are synchronous and may block a worker while the client or proxy applies
backpressure; this is the expected MVC difference from WebFlux.

HTML is retained in at most the shared 8 KiB character buffer between writes. The adapter flushes the
Servlet output after each `HtmlFrame`. Deferred patches have no page-sized queue: each completed
region is encoded and flushed before the next result is collected. A servlet container or reverse
proxy can still add its own buffering, so production deployments should verify proxy settings with a
real streaming journey.

Blocking database or SDK calls remain application-owned. The MVC default tolerates short blocking
calls, but it is not an unlimited capacity promise. For known blocking workloads, provide a bounded,
observable dispatcher through your own `WogeSpringMvcHandlers` bean and size it from measurements.
The application that creates that dispatcher also owns its shutdown.

## Cancellation and failures

One coroutine owns one async response and all deferred-region children.

- The configured async timeout cancels the coroutine before completing the response.
- Servlet container errors cancel the coroutine.
- A failed body write propagates immediately and cancels outstanding child work.
- Normal completion closes the async lifecycle without closing the container-owned output stream.

The Servlet API does not proactively notify an application when an idle remote client disappears.
The adapter discovers that disconnect on a later failed write; long-lived M2 streams therefore need
protocol-level heartbeats. WebFlux can observe subscriber cancellation immediately, so cancellation
latency is intentionally not claimed to be identical across adapters.

An exception before the response is committed becomes a bodyless 500 and does not expose exception
text. Once bytes are committed, HTTP cannot send a second status; the connection terminates instead.
Typed Woge failures and redirects are mapped before body collection.

Tune the Servlet lifetime independently from per-region work:

```yaml
woge:
  mvc:
    async-timeout: 60s
  deferred:
    max-concurrency: 8
    region-timeout: 30s
```

The response timeout should exceed the region timeout enough to write controlled fallbacks and the
terminal patch frame.

Run the maintained example with:

```shell
./gradlew :woge-reference-spring-mvc:bootRun
```

Then open `http://localhost:8080/projects/woge`. The portable page is the same source used by the
WebFlux application.

See [ADR 0032](../adr/0032-async-servlet-spring-mvc-adapter.md) for the adapter tradeoffs and the
[server-adapter parity matrix](../architecture/server-adapter-parity.md) for executable coverage.

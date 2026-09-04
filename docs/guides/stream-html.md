# Buffer or stream HTML

Use the same Woge component functions for a stable test string or for bounded incremental output.
Choose the sink at the edge where the application already knows whether it is writing a small fragment
or a server response.

## Render a string for a test

`renderHtml` uses `BufferedHtmlSink` and returns the complete fragment:

```kotlin
val html = renderHtml {
    element("p") { text("Hello & goodbye") }
}
```

The result is `<p>Hello &amp; goodbye</p>`. Use this path for assertions and intentionally buffered
responses, not for a large page that should become visible incrementally.

## Write directly to another sink

`writeHtml` performs no page-sized buffering:

```kotlin
writeHtml(responseSink) {
    element("main") {
        projectHeader(project)
        projectTasks(tasks)
    }
}
```

`responseSink` is an `HtmlSink` supplied by the host adapter. Component code receives `HtmlWriter`, not
the sink itself, so normal values still pass through `text`, quoted attributes and validated URLs.

## Coalesce small writes into bounded chunks

HTML serialization naturally produces small pieces such as `<main`, one attribute and `>`. Use
`streamHtml` to avoid one host call per piece while retaining only a bounded character buffer:

<!-- snippet: modules/woge-core/src/test/kotlin/dev/woge/html/HtmlSinksTest.kt -->

```kotlin
streamHtml(responseSink, maxChunkChars = 8 * 1024) {
    element("main") {
        projectHeader(project)
        projectTasks(tasks)
    }
}
```

The default is 8 Ki characters. This is a character bound, not a network packet, byte frame or promise
that a proxy/browser displays each callback immediately. The host still controls UTF-8 encoding,
response flushing, compression and proxy configuration.

A supplementary Unicode character is never divided between chunks. With the artificial edge case
`maxChunkChars = 1`, a two-code-unit chunk is allowed to keep that character intact.

## Let failures end the render

If the downstream sink reports a disconnect, cancellation or write failure, Woge rethrows the same
failure. It does not retry and cannot replace an already streamed prefix with an error page. The host
adapter decides whether the response was committed and records a safe diagnostic.

`streamHtml` flushes its final partial chunk only when the render completes normally. When using
`StreamingHtmlSink` directly, call `flush()` at a render or protocol boundary. `flush()` does not flush
or close the HTTP response itself.

See [ADR 0021](../adr/0021-synchronous-bounded-html-sinks.md) for lifecycle ownership and the
[first benchmark baseline](../performance/html-sinks-baseline.md) for current allocation/throughput
evidence.

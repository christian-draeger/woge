# Streaming behind proxies

A server calling `flush()` does not guarantee that a browser sees a chunk immediately. A reverse
proxy, compressor, CDN or browser can hold those bytes. Test the complete path, not just your page
function. Plain Woge HTML/CSS applications need no Node installation for these checks.

## What Woge guarantees

Deferred work is a child of one response. Cancellation or a failed write ends collection, cancels
its children and runs their `finally` cleanup. Do not launch request work in an unrelated scope.
Use cancellable database/HTTP clients and close resources in `finally` or `use`. Blocking application
code that ignores interruption cannot be made cancellable by a host adapter.

There is an important host difference:

- **Spring MVC / Tomcat:** a passive disconnect is not necessarily visible while no bytes are being
  written. The next failed write or Servlet async error/timeout cancels work. A bounded async timeout
  is therefore necessary. The contract sends TCP reset and subsequent small writes to verify this;
  it does not claim instant cancellation of an idle Servlet response.
- **Spring WebFlux / Reactor Netty:** disconnect disposes the response publisher. The coroutine-flow
  bridge cancels its collection and outstanding children.
- **Ktor / Netty:** response-writer cancellation or write failure ends its suspending collection and
  cancels children. Other Ktor engines must be measured separately.

The shared fixture checks all three without skipping MVC. A suspended child's `finally` signals
resource release within five seconds of a real socket reset. Existing Servlet timeout and Ktor
failed-write tests cover the other lifecycle paths.

### Slow clients do not create an unbounded patch queue

Reuse the [resource budgets](resource-budgets.md), not an extra application queue:

- Deferred declarations default to 128; at most eight workers run. The result channel is rendezvous
  (zero queued results). A blocked collector retains one result; active workers can retain at most
  eight more while holding their permits. Transport/socket buffers are separate from this bound.
- Streams default to 128 patches and 16 MiB. A budget failure ends the stream without a success marker.
- Live invalidations use the existing bounded set of declared targets. Repeated changes to a target
  merge before a region GET renders its latest Replace patch. The browser serializes those refreshes.
  This is #122's latest-only design; there is no second rendered-HTML replacement queue. Never drop
  append/remove patches or already revisioned replacements arbitrarily.

The executor's slow-collector test and live stream's coalescing test preserve these existing bounds.
See [ADR 0065](../adr/0065-request-owned-resource-admission.md) and
[ADR 0069](../adr/0069-live-invalidations-over-sse.md).

## Safe response and proxy settings

Patch and SSE responses now send:

```http
Cache-Control: no-store, no-transform
X-Accel-Buffering: no
```

`no-store` prevents cached user-specific updates. `no-transform` asks intermediaries not to change
or compress the representation; it is not an enforcement mechanism. `X-Accel-Buffering: no` tells
nginx not to buffer, unless the administrator ignores that header. nginx normally consumes this
header rather than forwarding it, so its absence at the browser does not prove buffering.

For streamed HTML, add the same headers to the page's `ResponseMetadata.headers` explicitly. Woge
cannot assume every document streams: intentionally cacheable, buffered pages keep their cache policy.
Use `httpHeader` and `ResponseHeaders.of`, as shown in the test-only diagnostic fixture.

Start with compression **off on streaming routes**, at both origin and proxy. Keep compression and
long-lived immutable caching on static assets. If you enable gzip/Brotli for streams later, measure
flush boundaries and time-to-first-byte again with `Accept-Encoding` set to that encoding. Small
chunks can remain inside a compressor until the response ends even when proxy buffering is off.
Do not set `Content-Length` for an unknown-length stream, or set `Transfer-Encoding` yourself.

The [nginx configuration](../../testing/proxies/nginx.conf) disables response buffering, proxy cache,
upstream retries and gzip, uses HTTP/1.1 upstream and bounds idle reads. Replace `@UPSTREAM@` with
`127.0.0.1:8080` and `@PORT@` with your listen port when using it outside the test tool. nginx's normal
`proxy_buffering` default is **on**; its usual read timeout is 60 seconds between reads, not a total
request deadline. Buffering on alone did not hold these tiny flushed identity chunks in our test:
never infer timing from one setting alone. The tool also ignores `X-Accel-Buffering` and enables gzip
in a deliberately unsafe control, where it detects end-only delivery.

The [Caddy configuration](../../testing/proxies/Caddyfile) uses `flush_interval -1` (immediate flushing),
HTTP/1.1 upstream and a ten-second read timeout, with **no `encode` directive**. Caddy's ordinary
reverse-proxy flushing is periodic; its automatic exceptions for SSE/unknown-length responses are
not a reason to skip measurement. Negative flush intervals also change disconnect propagation:
Caddy documents that they do not cancel the backend request immediately on an early client disconnect.
Keep upstream timeouts and cancellable work even when this explicit low-latency profile is used.

Production deployments should also terminate TLS, authenticate before streaming, restrict backend
access to the proxy, and trust forwarded host/protocol headers only from that proxy. Do not expose the
test fixture publicly. A diagnostic endpoint can otherwise become a slow-request resource sink.

## Spring Boot defaults and overrides

These tests use Spring Boot 4.1.1. Boot's `server.compression.enabled` defaults to `false`. Enabling it
normally uses a 2 KiB minimum response size and a MIME allowlist including HTML; tiny tests alone do
not prove your larger pages stream when compression is enabled.

A starting production configuration with the Woge starter is:

```yaml
server:
  compression:
    enabled: false
spring:
  mvc:
    async:
      request-timeout: 60s
woge:
  mvc:
    async-timeout: 60s
  deferred:
    region-timeout: 30s
```

`spring.mvc.async.request-timeout` governs Spring-managed asynchronous controllers. When unset,
Spring uses the container default (Tomcat typically 30 seconds). Woge's Servlet handler sets its own
`AsyncContext.timeout` from `woge.mvc.async-timeout` (60 seconds by default); changing only the Spring
MVC property does **not** override Woge. Without the starter, configure `WogeSpringMvcHandlers`
directly. The total response timeout should exceed the region timeout and allow writes to finish.
Every filter and the servlet must support async. Avoid response wrappers that buffer the whole body.

Reactor Netty does not use Servlet async timeouts; Spring MVC properties have no effect on WebFlux.
Configure Netty connection/idle policy through the Boot-supported server customizer for your version,
and keep application work deadlines explicit. A connection idle timeout is not a per-region timeout.
Do not block its event-loop threads. Neither changing Tomcat connection timeout nor disabling HTTP
keep-alive replaces a request-work deadline.

For SSE, set the proxy's idle read timeout above the configured heartbeat interval; allow the chosen
maximum stream lifetime, or expect reconnect/resync. The ten-second proxy fixture timeout is only
for this 1.5-second diagnostic, not a blanket production SSE recommendation.

Ktor has no Spring Boot properties. Configure the engine's idle/write timeouts, and do not install
its Compression plugin on patch/SSE/streamed HTML routes without measuring it. The adapter's region
policy still bounds work; engine/socket buffering is an additional layer.

## Reproduce the measurements

The **test-only** `/woge-tck/pages/stream-diagnostic` route emits four typed HTML frames containing
`chunk=0..3` and server timestamps, 500 ms apart. It is bound only by the TCK harness, never enabled
by the starter or production adapters. All hosts run the same portable fixture.

Run the direct contracts (no proxy installation required):

```shell
./gradlew :woge-spring-mvc:test :woge-spring-webflux:test :woge-ktor:test --tests '*AdapterTckTest'
```

Run the nginx comparison; Docker must be running:

```shell
docker pull nginx:1.28-alpine
WOGE_STREAM_PROXY_SCRIPT="$PWD/testing/proxies/stream-probe.py" \
  ./gradlew :woge-spring-mvc:test :woge-spring-webflux:test :woge-ktor:test --tests '*AdapterTckTest'
```

The existing Build workflow runs this on Ubuntu with a three-minute step timeout. Each probe has a
socket timeout, a bounded subprocess lifetime and container cleanup. Linux uses host networking to
reach loopback-only fixture hosts; Docker Desktop uses `host.docker.internal` and published loopback
ports. No application or proxy is left running.

Reproduce Caddy locally using the same fixture and configuration:

```shell
docker pull caddy:2.10-alpine
WOGE_STREAM_PROXY_PROFILE=caddy \
WOGE_STREAM_PROXY_SCRIPT="$PWD/testing/proxies/stream-probe.py" \
  ./gradlew :woge-spring-mvc:test :woge-spring-webflux:test :woge-ktor:test --tests '*AdapterTckTest'
```

With a deliberately enabled diagnostic in your own local application, run Caddy without Docker:
`WOGE_UPSTREAM=127.0.0.1:8080 caddy run --config testing/proxies/Caddyfile`.
Then run `python3 testing/proxies/stream-probe.py http://127.0.0.1:9080/woge-tck/pages/stream-diagnostic`.
For any stream, `curl --http1.1 -N --trace-time --trace-ascii build/stream.trace URL` records arrival
spacing; add `-w '\nTTFB=%{time_starttransfer} total=%{time_total}\n'` for timings. Curl's TTFB includes
response headers; the tool's `firstMs` measures the first **body** bytes, a stricter buffering test.

## Measured baseline, not a deployment SLA

Local macOS / Docker Desktop, 2026-10-11; Spring Boot 4.1.1, Ktor 3.5.2, nginx 1.28 and Caddy 2.10.
Fresh HTTP/1.1 requests, four 41-byte HTML frames, no TLS. The range includes all three hosts and both
`Accept-Encoding: identity` and `gzip`. The tool records each arrival, actual compression and framing.

| Host / container | Direct first / end (ms) | Caddy first / end (ms) | Cancellation evidence |
| --- | --- | --- | --- |
| Spring MVC / embedded Tomcat | 3–9 / 1522–1534 | 3–13 / 1521–1537 | TCP reset + next write releases child; async timeout also tested |
| Spring WebFlux / Reactor Netty | 3–8 / 1531–1532 | 3–11 / 1525–1532 | TCP reset cancels publisher/child |
| Ktor / Netty | 3 / 1528–1530 | 3–12 / 1532–1533 | TCP reset cancels response writer/child; failed-write unit test |

| Proxy profile, all hosts | First body (ms) | End (ms) | Compression / arrival behavior |
| --- | --- | --- | --- |
| nginx recommended | 4–14 | 1520–1540 | Identity; four arrivals approximately 500 ms apart |
| nginx buffering on, ignore header, gzip enabled; identity request | 3–5 | 1520–1523 | Still streamed in this fixture; configuration alone is not proof |
| Same unsafe profile; gzip request | 1525–1541 | 1525–1541 | gzip; one 90-byte arrival at response end, detected by probe |

All measured responses used HTTP/1.1 chunked transfer, including gzip: **chunked does not mean
unbuffered**. HTTP/2 and HTTP/3 use DATA frames, not HTTP/1.1 chunk framing. TLS/ALPN, HTTP/2/3,
other Servlet/Ktor engines, CDNs and real network congestion were not measured here. Re-run the
arrival trace through your actual public URL; do not copy these localhost numbers into an SLA.

See [ADR 0080](../adr/0080-streaming-deployment-contract.md) for the common contract and limits.

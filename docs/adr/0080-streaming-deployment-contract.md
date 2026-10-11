# ADR 0080: Verify streaming at the deployment boundary

- Status: Accepted
- Date: 2026-10-11
- Decision owners: Woge maintainers
- Related issues: [#45](https://github.com/christian-draeger/woge/issues/45), [#122](https://github.com/christian-draeger/woge/issues/122)
- Builds on: [ADR 0065](0065-request-owned-resource-admission.md), [ADR 0069](0069-live-invalidations-over-sse.md)

## Context

Flushing a host response is not proof of timely browser delivery. Compression and reverse proxies
can retain a complete response, and Servlet disconnect detection differs from reactive transports.
The existing request budgets already bound pending deferred results and coalesce live invalidations.

## Decision

Keep structured request cancellation and existing bounded queues, rather than introducing another
patch broker. Deferred results remain rendezvous-only and hold worker permits while downstream is
blocked. Live notifications merge in the bounded declared-target set before rendering the latest
Replace response. Do not coalesce ordered append/remove or already revisioned patches indiscriminately.

Add a common real-socket reset contract on MVC/Tomcat, WebFlux/Reactor Netty and Ktor/Netty. A suspended
child must run its resource-release finally block. The fixture continues writing after reset so MVC
cannot skip cancellation evidence under an optional capability flag. Document that passive Servlet
aborts are only detected by a failed write, async error or bounded timeout; application-owned blocking
code and other containers do not silently acquire a stronger guarantee.

Patch and SSE responses add `Cache-Control: no-store, no-transform` and `X-Accel-Buffering: no` across
hosts. Streamed documents opt into these headers through existing response metadata; intentionally
cacheable pages retain their policy. Origin/proxy compression is disabled in the recommended profile.
Headers are guidance, not enforcement against an administrator overriding them.

A test-only typed HTML diagnostic emits four timestamped frames over 1.5 seconds. A stdlib-only Python
probe records first body bytes, chunk spacing, actual content encoding and HTTP framing. The existing
Build workflow tests nginx against the same three hosts, with a short timeout and owned container
cleanup. A deliberately unsafe gzip/buffering control verifies that end-only delivery is detected.
A Caddy profile is reproducible through the same tool; it is not another required CI matrix. Both
profiles use explicit flush settings and record default/container differences in the guide.

## Alternatives considered

- **Only assert a mocked flush call:** misses proxy/compressor buffering and real disconnects.
- **Skip MVC abort checks:** conceals a production lifecycle difference instead of measuring it.
- **Add a general rendered-patch coalescing queue:** duplicates #122 and risks revision ordering.
- **Install diagnostic routes in production by default:** exposes a slow-response resource sink.
- **Add Node, browsers and multiple proxy CI jobs:** unnecessary for an HTTP arrival contract.

## Consequences

The common contract becomes stronger without new production public APIs or dependencies. Tests use
real in-process host servers and short-lived proxy containers. Queue tests remain owned by the runtime.
HTTP/1.1 chunked framing does not imply low latency; the unsafe compressed profile demonstrates this.
Caddy's negative flush interval and Servlet passive-disconnect behavior require explicit operational
timeouts. Published measurements are local evidence, not a deployment latency SLA or HTTP/2 promise.

## Follow-up

Measure TLS/ALPN, HTTP/2/3, additional containers/engines and the application's actual CDN/public path
before promising their timing. Keep versions and baseline measurements current. See the
[streaming deployment guide](../guides/streaming-behind-proxies.md) for reproducible profiles and
Spring Boot defaults/overrides; no unbounded queue or automatic mutation replay is an acceptable fix.

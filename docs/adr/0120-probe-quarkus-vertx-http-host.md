# ADR 0120: Use the Quarkus-managed Vert.x HTTP router for Woge

- Status: Accepted
- Date: 2026-11-25
- Decision owners: Woge maintainers
- Related issues: [#298](https://github.com/christian-draeger/woge/issues/298)

## Context

Woge's portable host boundary carries final response metadata and cold HTML or patch frames.
An adapter needs to commit metadata before the first chunk, enforce the byte budget and tie
collection to cancellation. The isolated JVM spike exercised early Unicode HTML, a failed write,
active TCP reset, bounded rendering and streamed portable deferred regions over a real connection.
[Quarkus JVM spike](../../spikes/quarkus-host/README.md)

## Decision

Use the managed Vert.x Router in `io.quarkus:quarkus-vertx-http`. Ship `woge-quarkus` as a peer
runtime library using the same Woge host SPI, protocol and server runtime as the other adapters.
Pin the JVM candidate to Quarkus 3.40.1.2 LTS, Vert.x 4.5.35, Kotlin 2.4.10 and JDK 21.
Register routes through an application CDI observer. Quarkus owns the HTTP server, authentication,
request scope, trusted proxies and logging providers. Woge owns portable request facts, application
execution, transport encoding and streaming cancellation. [Module graph](../architecture/module-graph.md) ·
[Quarkus HTTP reference](https://quarkus.io/version/3.40/guides/http-reference/)

Run request and response I/O on the owning event loop. Resolve CDI identity and origin there,
then hand the worker an immutable portable snapshot. Move rendering through a rendezvous channel,
await each socket write and wait for drain when the write queue is full. Cancel the request job on
close. Reset a committed response after an unexpected generation failure. Decode bounded form
and multipart input after portable action security has passed, and stage temporary files off the
event loop. Applications schedule their own blocking business I/O. [Quarkus Router security](https://quarkus.io/version/3.40/guides/security-customization)

Use the same `WogeObserver` port and Kotlin Logging facade with the Quarkus-provided provider.
Keep Quarkus, Jakarta, Vert.x and Mutiny types out of portable modules. Enable WebSockets explicitly
with a server subprotocol and an application upgrade policy. Serve ordinary HTTP actions and SSE
by default.

## Alternatives considered

Quarkus REST can stream a Mutiny publisher and cancel it when a request disconnects. Supporting it
would require a second bridge from suspending frame producers to publishers and tests for raw-byte
flushing, response filters and late termination across that bridge. The Router supplies explicit
write completion, queue drain, close and reset with one Kotlin coroutine per exchange. The consumer
needs a CDI observer and no build-time integration, so use a runtime library. The independent
Native Image build also works with the ordinary Quarkus application build.
[Quarkus REST streaming](https://quarkus.io/version/3.40/guides/rest/#streaming-support) ·
[Quarkus extension development](https://quarkus.io/version/3.40/guides/building-my-first-extension)

## Consequences

The shared real-server TCK exercises the binding against Vert.x HTTP, and the standalone packaged
application checks actual managed Quarkus identity, classpath assets and user journeys. The native
smoke configuration was tested separately on Linux ARM64 with Mandrel JDK 25. The JVM TCK and
separate native job define the scope of the candidate. The dependency closure, transport support
and focused CI are documented with the adapter. [Quarkus adapter](../../adapters/woge-quarkus/README.md) ·
[Standalone consumer](../../examples/quarkus-consumer/README.md)

## Follow-up

Use these bindings in the [multi-host showcase](https://github.com/christian-draeger/woge/issues/299)
when adding Quarkus to its correctly matched measurement matrix. Public distribution and version
commitments follow [the release work](https://github.com/christian-draeger/woge/issues/285) and
[the compatibility policy](https://github.com/christian-draeger/woge/issues/62).

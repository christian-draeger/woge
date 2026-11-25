# Woge on Quarkus

`dev.woge:woge-quarkus` connects Woge pages, actions and streams to the Quarkus-managed
Vert.x Router. The JVM candidate targets Quarkus 3.40.1.2 LTS, Vert.x 4.5.35, Kotlin
2.4.10 and JDK 21. The [standalone consumer](../../examples/quarkus-consumer/README.md)
resolves the explicitly versioned `0.1.0-quarkus-preview.1` candidate from a local Maven
repository. Public publication is tracked in [#285](https://github.com/christian-draeger/woge/issues/285).

## Use the adapter

Add `io.quarkus:quarkus-vertx-http` and `dev.woge:woge-quarkus` to your Kotlin
application. Register ordinary Router handles in a CDI observer as shown in the
[consumer routes](../../examples/quarkus-consumer/src/main/kotlin/example/quarkus/Routes.kt).
Keep the generated page inputs and form decoders in portable code. The host handles
route patterns, middleware, static assets and provider setup. Register HEAD where the
same page should answer it. Mount form routes before middleware that consumes their
request bodies, so Woge can authorize, decode and cap the body as it arrives.

Copy principal, roles and verified request facts with `QuarkusRequestContextFactory`
on the event loop. `quarkusRequestContext` can use Quarkus's effective request URI for
same-origin checks, or accept `RequestSecurity` validated by application middleware.
Configure Quarkus to trust only your intended proxy hops and public hosts. Work after
suspension reads immutable `RequestContext`; persistence or other synchronous I/O in
portable code belongs on a bounded I/O executor chosen by the application. The adapter
runs HTML rendering and portable work off the event loop, stages uploads on the I/O
dispatcher, waits for write-queue drain and cancels work when the HTTP response closes.

Send semantic events through `WogeObserver`. The packaged application connects the
port to Quarkus-managed logging. The library uses the Kotlin Logging facade over the
SLF4J API; the packaged dependency check verifies Quarkus's own log manager binding.
An application can also connect its own metrics or tracing through the same port.

## Capability matrix

| Capability | Evidence and scope |
| --- | --- |
| Documents, HEAD, headers, caching and error mapping | Shared real-HTTP TCK and packaged static assets |
| Typed POST, authorization and origin policy, native 303, validation, enhanced patches and multipart | Shared TCK, shared Chromium form tests and packaged form journeys |
| Deferred patches, early flush, byte limits and terminal errors | Shared TCK, slow-reader and disconnect checks, packaged native and enhanced forecast |
| Managed live regions and SSE | Shared TCK and a consumer route |
| Authenticated request context | Managed Quarkus HTTP test with authenticated, forbidden and anonymous requests |
| WebSocket patches | Opt-in binding verified by the shared Vert.x socket TCK. Set the negotiated `woge.patch.v1` subprotocol with Quarkus `HttpServerOptionsCustomizer`, set transport size limits and provide an application authorizer before enabling it. |
| Initial-document DPU | Experimental, off by default. Shared real-server and Chromium tests exercise eligibility, initial HTML and fallback. |
| Linux Native Image | Narrow smoke profile with pinned Mandrel JDK 25. Linux ARM64 built and ran packaged HTML, CSS and streamed forecast. The full adapter contract is the JVM support gate. |

The [adapter CI](../../.github/workflows/quarkus-adapter.yml) runs the full JVM contract,
ABI and module checks, the unpacked app and packaged Chromium, Firefox and WebKit journeys.
Its separately triggered Native Image smoke checks the small configuration above. See the
[integration ADR](../../docs/adr/0120-probe-quarkus-vertx-http-host.md), [Quarkus security for
Router handlers](https://quarkus.io/version/3.40/guides/security-customization#securityidentity-injection-in-vert-x-route-handlers)
and [Quarkus HTTP server options](https://quarkus.io/version/3.40/guides/http-reference/#arbitrary-customizations).

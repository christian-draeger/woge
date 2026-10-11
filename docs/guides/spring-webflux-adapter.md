# Run a Woge page with Spring WebFlux

Woge keeps the page itself independent from Spring. A small WebFlux bootstrap connects that page to
normal functional routes; there is no application controller and no Reactor type in page code.

## Start with the same page and action

Run the [page/form quickstart](quickstart-spring-boot.md). The host-neutral
[NotesPage and AddNoteAction](../../examples/reference-application/shared/src/main/kotlin/dev/woge/example/notes/Notes.kt)
import Woge HTML/host types, not Spring or Reactor. Deferred data loaders are suspending Kotlin
functions too; do not block the WebFlux event loop.

<!-- snippet: examples/reference-application/shared/src/main/kotlin/dev/woge/example/notes/Notes.kt -->

## Connect functional routes

Inject Boot's `WogeWebFluxHandlers`. The compiled
[route configuration with complete imports](../../examples/reference-application/spring-webflux/src/main/kotlin/dev/woge/example/ProjectRoutes.kt)
keeps URLs and methods explicit:

<!-- snippet: examples/reference-application/spring-webflux/src/main/kotlin/dev/woge/example/ProjectRoutes.kt -->

```kotlin
val page = handlers.page(NotesPage(), NotesRoute)
val action = handlers.action(AddNoteAction, addNoteForm.webFluxInput())
return coRouter {
    GET(NotesRoute.path, page::handle)
    POST(AddNoteAction.path, action::handle)
}
```

For independently completing content, use [deferred regions](deferred-regions.md) with
`request.context.render`, generated targets and a no-JavaScript link to the complete page.
For SSE-driven data, `handlers.liveRegion(...)` binds the document and refresh to one page route;
see [live regions](live-updates.md). Application code does not reconstruct protocol targets.

Choose MVC for Servlet integrations or blocking persistence; WebFlux requires explicit dispatch
for blocking work. The [support matrix](../architecture/spring-support-model.md) and
[measured baseline](../performance/m1-multi-host-baseline.md) describe constraints, not universal speed.

The default context mapper treats requests as anonymous. Page and deferred handlers use safe methods;
action bindings use the built-in same-origin CSRF check, so a normal browser form needs no custom
factory. A matching Origin is accepted, or `Sec-Fetch-Site: same-origin` when Origin is absent or
`null`; other unsafe requests receive 403 before decoding. For authenticated applications, provide a
`WebFluxRequestContextFactory` that translates the Spring Security principal and verified CSRF
decision. Domain authorization remains application-owned. Behind a TLS-terminating proxy, set
`server.forward-headers-strategy=framework` so the observed scheme and host match the browser URL;
trust forwarded headers only from a trusted proxy.

## Locale and sessions

The default request snapshot uses Spring's resolved locale. In MVC this respects `LocaleResolver`;
in WebFlux it respects `LocaleContextResolver` on the exchange. Authentication and verified token
CSRF facts remain explicit through a request-context factory; Woge does not infer domain permissions.

Spring owns the session. Read it in the input/context factory and copy only application facts into
portable inputs. Do not pass a mutable session or host request into page code. See the
[starter guide](spring-boot-starter.md#use-spring-locale-identity-and-sessions) for the host APIs.

## What failures mean today

A controlled `PageResult.Failure` maps to its Woge status before the response starts. A Spring
`ResponseStatusException` thrown by route decoding or application setup also remains a normal
pre-stream Spring error. Once bytes are committed, an exception ends the response and cancels its
coroutine work; it cannot change the existing status.

The [recovery model](mental-model.md#browser-modes-and-recovery) applies to every host. Never translate
a failed unsafe request into an automatic retry.

See [ADR 0028](../adr/0028-functional-spring-webflux-adapter.md) for lifecycle and buffering tradeoffs
and [the deferred-region guide](deferred-regions.md) for region declaration details.

For deployment timing, disconnect cleanup and operational settings, see
[streaming behind proxies](streaming-behind-proxies.md).

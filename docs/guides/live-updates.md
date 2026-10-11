# Live regions (Server-Sent Events)

A live region changes when the server has new data: another user added a task or a job finished.
Woge sends the region ID over SSE. The shipped browser client then makes a safe GET for current HTML.
The stream never carries HTML, and every refresh checks current access.

## Declare a live page

Mark the HTML function with `@WogeRegion`; the build generates `AnnouncementListRegion`:

```kotlin
@WogeRegion
internal fun HtmlWriter.announcementList(items: List<String>) {
    ul { items.forEach { li { text(it) } } }
}
```

Render it in your ordinary `PageUseCase`. `request.context.render` owns the page identity and ordering
state. Write its metadata in the HTML head and include the shipped fallback module (use your
[asset URL](production-assets.md) for the packaged client). There is no application JavaScript:

```kotlin
val render = request.context.render
// In head:
render.metadata(this)
moduleScript(applicationUrl("/assets/woge-fallback.js"))
// In main:
liveRegion(render, AnnouncementListRegion, repository.all(), AnnouncementsRoute.url(request.input))
```

Describe how to reload the data and which changes should invalidate it:

```kotlin
val live = LiveRegionPage(
    page = announcementsPage,
    region = AnnouncementListRegion,
    load = { request -> repository.all(request.input) },
    changes = { request -> repository.changes(request.input) }, // Flow<Unit>
    authorize = { request -> checkAccess(request) }, // Failure or null
)
```

`authorize` runs for the document, every refresh and every SSE reconnect. The default allows anonymous
access; protected pages must supply this check. Returning a failure stops that request before loading.
Keep `load` side-effect-free. The epoch and opaque region ID are not access tokens.

Bind just **one route**, the page's existing typed route:

```kotlin
val handler = handlers.liveRegion(live, AnnouncementsRoute)
// Spring WebFlux, inside coRouter:
GET(AnnouncementsRoute.path, handler::handle)
// Spring MVC: map AnnouncementsRoute.path to handler (an HttpRequestHandler).
// Ktor: get(AnnouncementsRoute.path) { handler.handle(call) }
```

The binding serves the document, safe region refresh and SSE at this same URL. Woge-owned query
parameters distinguish the requests; applications need no extra input classes or protocol imports.
Normal navigation still shows current data if JavaScript is disabled or the connection fails.

## Host configuration

Spring Boot binds `woge.render-identity.secret`. Supply a Base64 encoding of at least 32 random bytes
from your secret store. All instances serving a deployment must share it. Never put the secret in HTML,
source control or logs.

Ktor reads the same key with `WogeKtorHandlers(application.environment.config)`. Manual hosts can pass
`renderIdentitySecret` to their handler factory. Keep one handler factory for the application.

If unset, Spring Boot and the Ktor config constructor generate one random startup secret and warn.
This is safe for local development, but restarting invalidates old rendered IDs and independent
instances cannot refresh each other's pages. Configure the shared secret in production. Invalid or
short configured secrets fail startup; their values are never logged by Woge.

## Limits and browser behavior

Live bindings share the existing host-wide `LiveLimits`: bounded subscriptions, latest-only pending
invalidations, heartbeats, retry hints and maximum connection lifetime. SSE reconnects reauthorize and
resync the declared region. Browser refreshes are serialized per target, with at most one follow-up.
No server-side page session or event replay log is needed.

Updates preserve focus and are silent. Choose your own status text and accessibility policy; do not
add `aria-live` merely because content is live. If the server refuses a stream or refresh, the page
remains usable with normal navigation. Connections close on `pagehide`.

Advanced applications can still use `LiveUseCase`, `liveSubscription`, explicit region GETs and
`connectWogeLive` with their own loader, including multi-region subscriptions and per-session keys.
See [ADR 0069](../adr/0069-live-invalidations-over-sse.md) and
[ADR 0088](../adr/0088-host-owned-render-sessions-and-live-regions.md).

For genuinely bidirectional presence or high-frequency updates, see the
[optional WebSocket transport](websocket-updates.md). SSE plus HTTP actions remains the default.

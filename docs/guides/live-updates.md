# Live updates (Server-Sent Events)

Sometimes the server knows first that a page is out of date: another user added a task, a job
finished. Woge tells the browser **which regions changed**, using
[Server-Sent Events](https://developer.mozilla.org/docs/Web/API/Server-sent_events) (SSE). The
browser then reloads each changed region with the region GET you already have.

The event stream never contains HTML. Every reload is a normal GET that checks authorization
against current data. See [ADR 0069](../adr/0069-live-invalidations-over-sse.md) for the reasoning.

Live updates are an extra. The page works without them, and normal navigation always shows
current data.

## On the wire

```text
retry: 3000

id: 1
event: invalidate
data: r-4f1c…
data: r-9a07…

: heartbeat

```

- `retry` tells `EventSource` how long to wait before reconnecting.
- `invalidate` lists the region target ids that changed, one `data` line each.
- After a reconnect (the browser sends `Last-Event-ID`), the first event is `resync` and lists every
  region the stream covers. That replaces anything missed while disconnected.
- Lines that start with `:` are heartbeats that keep proxies and dead-connection detection working.

Responses use `text/event-stream`, `Cache-Control: no-store` and `X-Accel-Buffering: no`, so caches
and nginx-style proxies pass events through right away.

## Server: declare a live route

A live route is a typed GET like any page. Its use case checks access and returns which regions the
browser may refresh, plus a `Flow` that emits a region whenever it changed.

```kotlin
val boardLive =
    LiveUseCase<BoardLiveInput> { request ->
        if (!canSeeBoard(request.context)) {
            liveRefused(FailureCategory.FORBIDDEN, request.context.correlationId)
        } else {
            val page = PageIdentity(PageEpoch.of(request.input.epoch), secret)
            val tasks = BoardTasksRegion.target(page)
            liveSubscription(
                targets = listOf(tasks),
                invalidations = boardChanges.map { tasks }, // boardChanges: SharedFlow<Unit>
                session = LiveSessionKey.of(sessionIdOf(request.context)),
            )
        }
    }
```

Bind it with the same handler factory as your pages:

```kotlin
// Spring MVC
BoardLiveRoute.path to handlers.live(boardLive, BoardLiveRoute)
// Spring WebFlux
GET(BoardLiveRoute.path, handlers.live(boardLive, BoardLiveRoute)::handle)
// Ktor
get(BoardLiveRoute.path) { live.handle(call) }
```

Rules:

- Declare at most 128 regions. Emitting a region you did not declare ends the stream, because it is a
  programming error.
- Several changes to the same region while the browser is busy are merged into one event. Nothing
  queues up, so do not use the stream as a message log. A notification list is just another region.
- The stream ends after `maxLifetime` (30 minutes by default) and when your `Flow` completes. The
  browser reconnects, and your use case checks access again. Complete the flow yourself when a
  user logs out or loses access.
- The application-wide and per-session limits are described in
  [resource budgets](resource-budgets.md#live-streams).

## Spring MVC note

Live streams use Servlet async support, like deferred regions. The handler sets its own async
timeout just above `maxLifetime`, so you do not need to raise `asyncTimeout` for it.

## Browser

The fallback client's live connector opens one `EventSource` and reloads every changed region with
your region GET. Its API and the reference-application example are tracked in
[#38](https://github.com/christian-draeger/woge/issues/38).

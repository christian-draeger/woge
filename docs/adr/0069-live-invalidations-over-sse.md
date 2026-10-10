# ADR 0069: Push live invalidations over SSE and refresh regions with normal GETs

- Status: Accepted
- Date: 2026-10-10
- Decision owners: Woge maintainers
- Related issues: [#38](https://github.com/christian-draeger/woge/issues/38), [#122](https://github.com/christian-draeger/woge/issues/122)
- Builds on: [ADR 0047](0047-canonical-failure-and-recovery-model.md), [ADR 0048](0048-document-owned-accessibility-announcements.md), [ADR 0062](0062-latest-intent-and-bounded-region-recovery.md), [ADR 0065](0065-request-owned-resource-admission.md)

## Context

Applications need server-initiated updates: another user added a task, a job finished. The obvious
design pushes rendered HTML patches to each subscriber. Then every subscriber needs its own render,
authorization is checked only once when the connection opens, a slow client builds up a queue of
HTML, and reconnecting needs a server-side event log so nothing is missed or applied twice.

Woge already has an authorized, side-effect-free way to load the current state of one region: a
typed GET that returns one Replace patch (ADR 0062). Browsers already have a standard, reconnecting
one-way channel: `EventSource` (Server-Sent Events).

## Decision

**SSE carries only invalidations: "these regions changed".** It carries no HTML. The browser then
loads each changed region with the application's normal region GET. That GET authorizes against
current data like any other request.

Server side:

- A live route is a typed GET. Its use case authorizes the request and returns either a normal
  failure status or a subscription. A subscription declares its region targets up front (at most
  128) and supplies a `Flow` of invalidated targets. Invalidating an undeclared target is a
  programming error and ends the stream.
- The runtime keeps a set of pending targets. A burst of invalidations for the same region becomes
  one event (latest-only). The set cannot grow beyond the declared targets, so a slow client cannot
  make the server buffer more. There are no non-coalescible live events: a notification feed is a
  region the browser refreshes like any other.
- Events have monotonically increasing ids within one connection. A browser that reconnects sends
  `Last-Event-ID`; Woge then starts with one `resync` event listing every declared target. This
  covers anything missed while disconnected without a server event log. Refreshing is idempotent,
  and the browser ignores stale revisions, so nothing is applied twice.
- The stream starts with a `retry` hint, sends a comment heartbeat while idle and closes after a
  maximum lifetime. The browser reconnects automatically, so authorization and session expiry are
  re-checked at least once per lifetime. The application can also complete its flow to force that.
- Admission is bounded before the stream starts (#122): a per-application subscription limit
  returns 503 and an optional per-session limit returns 429. The application chooses the session
  key, for example its login session. All limits have safe defaults and explicit overrides.
- Responses use `text/event-stream`, `Cache-Control: no-store` and `X-Accel-Buffering: no`.
  Spring MVC, Spring WebFlux and Ktor share one runtime writer. No Reactor, `SseEmitter` or Ktor
  channel type enters the public API.

Browser side:

- The fallback client connects one `EventSource` and asks an application-supplied loader for each
  invalidated or resynchronized target. Refreshes are serialized per target with at most one
  follow-up, which is the browser's latest-only bound.
- Live refresh is not recovery: it does not use the once-per-revision recovery budget, but it uses
  the same Replace validation, ordering and stale-revision checks.
- Updates are silent and keep focus (ADR 0048). A page that wants an announcement renders its own
  `role="status"` summary inside the refreshed region.
- If the server refuses the connection (403, 429, 503), `EventSource` stops. The page keeps working
  without live updates; normal navigation still shows current data.

## Alternatives considered

- **Push rendered patches:** a render per subscriber, authorization only at connect time, HTML
  queues for slow clients and a replay log for reconnects.
- **WebSocket:** two-way and not needed for one-way updates; proxies, auth and reconnect would all
  have to be rebuilt. It remains optional future work (#57).
- **Polling:** simple, but it wastes requests and adds latency for every region.
- **A server-side replay log keyed by `Last-Event-ID`:** more state and memory. Resync gives the same
  correctness for the small number of regions a page declares.

## Consequences

Each update costs one extra request for a changed region, the same request a user would make by
reloading. In return there is no per-subscriber rendering, no HTML buffering and current
authorization on every load. Reconnecting refreshes all declared regions once.

The TCK checks headers, retry hint, heartbeat, coalescing, resync on reconnect, admission limits,
authorization failures and cleanup on disconnect in all three hosts. Browser tests check refresh,
focus and the reference application's live notification.

## Follow-up

The server port, shared encoder, admission limits and all three host handlers close #122. The
fallback-client live connector and the reference task board follow under #38. Reverse-proxy fixtures
belong to the deployment work in #45.

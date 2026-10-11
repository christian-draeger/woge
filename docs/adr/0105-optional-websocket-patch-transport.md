# ADR 0105: Keep WebSocket patch delivery optional and bounded

- Status: Accepted
- Date: 2026-10-11
- Decision owners: Woge maintainers
- Related issues: [#57](https://github.com/christian-draeger/woge/issues/57)
- Builds on: [ADR 0010](0010-identity-epochs-and-revisions.md), [ADR 0024](0024-strict-bounded-patch-stream-codec.md), [ADR 0069](0069-live-invalidations-over-sse.md), [ADR 0088](0088-host-owned-render-sessions-and-live-regions.md), [ADR 0092](0092-atomic-patch-transactions.md)

## Context

SSE invalidations plus authorized region GETs remain a good default. Collaborative cursor movement
is different: clients continuously publish short-lived positions and receive other participants'
updates. An HTTP request for every position adds unnecessary requests. Issue #57 needs an explicit
escape hatch without another patch model, RPC dependency, DOM renderer or core-client byte increase.

## Decision

Bind an optional WebSocket endpoint explicitly in MVC, WebFlux or Ktor. Share the session engine and
policy in host-spi. No routes or connections are installed automatically. Native navigation and
HTTP actions remain authoritative; ephemeral messages are never retried.

Upgrade requires one matching non-null Origin, the `woge.patch.v1` subprotocol and exactly one
`woge.csrf.<proof>` offered subprotocol. An application-supplied typed authorizer checks authentication,
endpoint permission and the session-bound proof before upgrade. Only `woge.patch.v1` is selected;
proofs are not echoed or put in URLs. Host security filters/context factories stay visible. The
application authorizes every subscription and ephemeral message against current resource permissions.

Bounded JSON commands subscribe/unsubscribe/message/pong multiplex up to 16 subscriptions by default.
A subscription supplies the existing page epoch and a map of region IDs to existing target revisions,
encoded as decimal strings to avoid JavaScript number precision loss. These values are not capabilities.
Applications return existing `Flow<Patch>` values and own a bounded replay log. Resume emits exactly
contiguous target steps; missing history, foreign epochs/targets or gaps refuse/close the connection.
A fresh document GET is the recovery path; Woge never invents revisions or snapshots over a gap.

A binary WebSocket message contains a one-byte ASCII subscription-ID length, that ID, then a complete
ordinary PatchStreamV1 stream with one patch unit and Complete. Replace, Append, Remove and atomic
transactions retain their existing encoding, safety, ordering, focus and document-owned announcements.
The client passes these bytes to its existing runtime, serially, without a parallel DOM/patch model.
SSE itself still carries invalidations, not HTML; HTTP patch-stream bytes and the WebSocket payload
use the same neutral codec.

Application-level ping/pong works with browser WebSocket APIs, which do not expose protocol ping.
Defaults: ping every 15 seconds, 45-second idle/send timeout, forced authorization reconnect after
30 minutes, 64 inbound commands/second, 4 KiB inbound messages, eight queued outbound frames and
512 KiB per outbound message. Overflow closes with 1013; malformed/rate-limited/unauthorized input
closes with 1008. Queues do not drop revisioned patches silently. Disconnect cancels subscription
children. Host network buffers also require deployment limits.

The separate `@woge/fallback-client/websocket` asset has its own fixed budget. It takes the existing
runtime, reads applied DOM revisions before reconnect, retries with bounded jittered exponential
backoff, answers heartbeats and bounds incoming pending frames and outgoing buffered bytes/messages.
Policy/application errors stop retries. The core bundle remains unchanged. JVM classpath delivery
ships the optional module without requiring Node or Vite in plain applications.

## Alternatives considered

- **Replace SSE with WebSocket everywhere:** unnecessary connection state, replay logs and operational cost.
- **Send a new JSON patch model:** duplicates Patch IR validation and browser semantics.
- **Automatically replay client messages:** unsafe for mutations and wrong for obsolete cursor positions.
- **Unbounded queues or silently coalesce revisioned patches:** hides gaps and consumes arbitrary memory.
- **Push HTML over existing SSE:** changes its invalidation/authorized-GET contract without a bidirectional benefit.

## Consequences

### Positive

- All three hosts run the same real-upgrade, authorization, multiplex, resume and cursor-message TCK.
- Virtual-clock tests cover heartbeat, disconnect cleanup, rate limiting and slow-consumer closure.
- Normal forms, HTTP actions, native pages, SSE and core-client ceilings remain unchanged.

### Negative

- Applications own replay retention, per-resource authorization and recovery after history expiry.
- WebSocket deployments need upgrade proxy configuration, connection admission and bounded network buffers.
- This is an advanced low-level binding, not an automatic replacement for managed live regions.

## Follow-up

- Follow the [WebSocket guide](../guides/websocket-updates.md); the cursor TCK is a test fixture, not a production broker.
- Measure real deployment latency, replay retention and connection limits before adopting this transport.
- Durable collaborative editing and cross-instance presence storage remain application responsibilities.

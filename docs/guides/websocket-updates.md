# Optional WebSocket updates

**Start with [SSE live updates](live-updates.md) and ordinary HTTP actions.** Most dashboards, job
status pages, notification feeds and forms do not need WebSocket. SSE already reconnects, coalesces
invalidations and refreshes through authorized GETs. Keep those defaults unless you can name a
bidirectional or high-frequency requirement.

## When not to use it

Do not use WebSocket just to save a button click, load a page, submit a form, upload a file, show an
occasional status change or avoid learning HTTP. It adds persistent connections, upgrade configuration,
security checks, replay storage and failure handling. It is not RPC or a new UI model. Your HTML,
links, forms and HTTP endpoints remain useful when JavaScript or the connection is unavailable.
Never send payments, saves or other durable actions through the ephemeral message callback.

## A reason to use it

Collaborative cursor/presence updates go both ways many times a second. The shared adapter test fixture
`CursorWebSocketFixture` has two cursor rooms. A client sends `12,34`; the server validates it and
renders an authoritative cursor position using `patchHtml { element("p") { text(...) } }`.
One socket carries both rooms. Each room keeps 32 replay patches. This avoids an HTTP request for every
pointer movement while keeping markup in the typed Woge DSL. It is deliberately a test fixture,
not a shared production presence service. Throttle pointer updates; do not send every browser event.

## Server essentials

Implement `WebSocketUseCase` from `dev.woge.host`:

- `subscribe(request, context)` checks current permissions and returns `Flow<Patch>`, or `null` to refuse.
  `Flow` is a sequence of values produced over time. Use existing Patch IR values, not JSON HTML patches.
- `message(request, payload, context)` validates and authorizes an ephemeral client message. Do not
  trust a subscription ID, page epoch, region ID or revision as authorization.
- Keep a bounded replay log per authorized page/target. On resume, replay patches strictly after each
  supplied revision. The engine checks the epoch, targets and contiguous revision steps, including
  transaction members. If history expired, refuse; offer the user normal page navigation to recover.
- Stop the flow when permissions expire. The handshake repeats at least every 30 minutes, but this
  does not replace checks against current session/resource permissions inside the use case.

Construct `WebSocketUpgradePolicy(publicHttpOrigin, WebSocketAuthorizer { context, proof -> ... })`.
The authorizer has **no default**. Integrate host authentication, endpoint authorization and verification
of a session-bound anti-CSRF proof. Use cryptographically unpredictable proofs and constant-time token
verification; do not copy the fixture's hard-coded proof. The browser offers it as a second subprotocol,
not a query parameter. Redact `Sec-WebSocket-Protocol` from request/access logs. The policy additionally
requires a single same-origin Origin header and the supported patch subprotocol before upgrade.
A missing/null/cross-origin Origin is refused even for non-browser clients.

Choose an explicit endpoint, for example `/presence`:

- **Spring MVC:** construct `WogeSpringMvcWebSocket(policy, useCase, contextFactory)`. In a normal
  `@EnableWebSocket`/`WebSocketConfigurer` configuration, register it with
  `registry.addHandler(socket, "/presence").addInterceptors(socket).setAllowedOrigins(publicHttpOrigin)`.
  The same object is the handler **and** mandatory pre-upgrade interceptor. Retain Spring Security.
- **Spring WebFlux:** construct `WogeWebFluxWebSocket(policy, useCase, contextFactory)`. It is a
  `WebHandler`; route that path to it using the host's WebHandler dispatch. Do not confuse this with
  a functional `HandlerFunction<ServerResponse>` or bypass the security filter chain. The suspending
  context factory can await reactive authentication. The optional `WebSocketService` parameter allows
  an explicitly configured host upgrade strategy.
- **Ktor:** install `WebSockets`, then call
  `wogeWebSocket("/presence", policy, useCase, contextFactory)` inside `routing`. The route-scoped
  upgrade policy runs before the handler. Retain Ktor authentication and bounded frame settings.

The default context factories are anonymous. Supply a security-aware factory for authenticated
applications. Woge does not infer permission from an open connection.

## Browser essentials

Import the separately delivered module, not more code into the core bundle:

```javascript
import { connectWogeWebSocket } from "@woge/fallback-client/websocket";

const presence = connectWogeWebSocket("wss://example.test/presence", {
  runtime, // the same WogePatchRuntime already used by this document
  proof: () => document.querySelector('meta[name="presence-proof"]').content,
  subscriptions: [{
    id: "cursors",
    epoch: pageEpoch,
    revisions: () => Object.fromEntries(cursorRegions.map(region => [
      region.getAttribute("data-woge-region"),
      region.getAttribute("data-woge-revision"),
    ])),
  }],
  onError: () => showDisconnectedMessage(),
});

presence.send("cursors", "12,34"); // false means not queued; do not retry an obsolete position
// presence.subscribe(...), presence.unsubscribe("cursors"), presence.close()
```

Use your document's existing epoch and current target elements. If replacements recreate those
nodes, resolve them again inside `revisions()`, rather than retaining detached elements. The callback
runs after pending patch application before reconnect. Read revisions as strings, not JavaScript
numbers. Use the current DOM value, never a local guess. Serve the optional `websocket.js` beside the
other classpath modules for JVM delivery, or copy the full npm `dist` directory. No Node/Vite is
needed by a plain JVM application. Allow the same-origin `wss:` endpoint in CSP `connect-src`.

Patches still use the existing runtime: validation, dirty-control preservation, latest intent,
atomic transactions and silent accessibility behavior do not change. Avoid simultaneous HTTP actions
and socket publishers advancing the same region without application-owned revision coordination.
On unrecoverable history/epoch mismatch, show a reconnect/reload link, not an automatic mutation retry.

## Bounds and operations

The default server limits are 16 subscriptions, 64 commands/second, 4 KiB commands, eight queued
frames and 512 KiB frames. Customize `WebSocketLimits` per binding. Application ping every 15 seconds
requires a pong within 45 seconds; sends are timed out too. Connections expire after 30 minutes to
rerun upgrade authentication. All subscription children are cancelled on disconnect or refusal.

A slow consumer closes with **1013**, not an unbounded queue or silently missing revisions.
Unauthorized, malformed or rate-limited input closes with **1008**. Application failures close with
**1011**, idle/lifetime timeouts with **1001**. No exception details or payloads enter close reasons.

The client queues at most eight pending frames, limits each frame to 512 KiB, rejects sends while
more than 64 KiB is buffered and sends at most 32 commands/second. Reconnect uses jittered exponential
backoff starting at 250 ms, capped near 10 seconds, with at most six attempts over this controller's
lifetime. It rereads the proof and applied revisions. 1008/1011 and local protocol/application errors
stop retries. It never replays client messages. Pass an AbortSignal or call `close()` on page disposal.

Configure your reverse proxy for WebSocket upgrades, TLS, idle timeouts longer than the heartbeat,
bounded network write buffers and per-user/global connection admission. For Servlet containers,
configure a finite network send timeout as well as Woge's coroutine timeout: blocking container I/O
must respond to interruption or its own timeout. Multi-instance deployments need an application-owned
presence/replay store or deliberate affinity. Test your real public path before claiming low latency.

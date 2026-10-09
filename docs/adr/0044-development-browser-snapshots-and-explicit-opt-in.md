# ADR 0044: Send development snapshots over SSE and opt in through the HTML DSL

- Status: Accepted
- Date: 2026-10-09
- Decision owners: @christian-draeger
- Related issues: [#145](https://github.com/christian-draeger/woge/issues/145)
- Builds on: [ADR 0041](0041-orchestrator-owned-spring-reload-and-sse-channel.md)

## Context

The application child restarts; the browser channel must not. Each tab needs to see build failures
without losing its last useful document, and refresh only when a new application generation is ready.
Reconnecting tabs need the current result, not an unbounded history of every keystroke.

## Decision

1. **A separate development-only module owns a stable SSE endpoint.** `woge-dev-browser` binds to
   `127.0.0.1`. It runs in the orchestrator's process, outside Spring/Ktor and without a reverse proxy.
   Node and a JavaScript bundler are not required to serve its plain JavaScript and CSS resources.
2. **Send complete snapshots, not commands.** Each snapshot has a protocol version, session identity,
   monotonic sequence, build and server generation, last ready document build, phase and redacted
   source-located diagnostics. IDs are decimal strings, so browser integer precision cannot lose them.
3. **Bounded latest-state replay.** Each connection receives the latest complete snapshot. This also
   answers reconnects with `Last-Event-ID`: the client can discard a duplicate sequence or apply a newer
   snapshot. An intermediate ready event remains represented by the last ready document build even if
   another build is already running. No unbounded event history is needed.
4. **Independent tabs and bounded connections.** Each tab has a one-snapshot queue and its own
   `EventSource`. Slow tabs cannot block the lifecycle coordinator. There are at most 16 active tabs by
   default (configurable up to 64); excess connections receive HTTP 503. Heartbeats and a 30-second
   connection lifetime bound stale connections; native `EventSource` reconnects.
5. **Explicit HTML opt-in.** `HtmlWriter.developmentClient` writes escaped configuration metadata,
   an external module script and optional stylesheet with the existing typed DSL. The caller supplies
   the build/generation that actually rendered the page. Production templates and artifacts do not
   acquire these resources or relaxed CSP rules automatically.
6. **Reject stale identities.** The client checks protocol shape, session, sequence, builds and
   generations before rendering or refreshing. A generation of zero means no live server. It reloads
   at most once per document, only for a newer ready document, and waits until online before reloading.
7. **The overlay is optional, ordinary accessible HTML.** It uses a polite status announcement, escaped
   text for diagnostics, a keyboard-operable hide button and normal CSS. It never steals form focus.
   A failed compile leaves the document and its controls untouched. A successful full reload makes no
   claim about preserving focus, scroll or dirty fields; that is #150.
8. **No privileged upstream SSE commands.** The event/detail endpoints require a random session
   credential, exact loopback Host and an explicitly allowed application Origin. Public generic assets
   carry no session data. CORS does not authorize arbitrary web origins. Credentials are omitted from
   debug representations. CSP allowances for the development script, stylesheet and connection must
   be supplied explicitly by the dev host, never by a production integration.
9. **Raw details remain separate.** A build adapter may explicitly supply a bounded raw-detail source.
   The authorized `/details` endpoint and overlay link are absent if it does not. Raw logs are never
   SSE diagnostics or primary status. This endpoint is local privileged development data.

## Alternatives considered

- **Put SSE in the application child.** Its connections disappear on every restart, and cannot
  describe a failed build while the child is unavailable.
- **Replay all events.** Adds history retention and backpressure without improving the current document
  decision. A ready-build watermark in the snapshot covers missed refreshes.
- **A bundled client framework.** Unnecessary for `EventSource`, ordinary HTML and a small status panel.
- **Inline scripts/styles.** Avoided so the development host can use explicit external-resource CSP
  allowances; diagnostics never enter an `innerHTML` or script-source boundary.

## Consequences

- MVC, WebFlux and Ktor share the same browser protocol; their production APIs are unchanged.
- JVM HTTP tests cover authentication, origins, concurrent tabs, reconnect, shutdown and tab budgets.
  Browser journeys cover multi-tab refresh, rapid saves, offline recovery, diagnostic escaping,
  source locations, keyboard behavior and stale identities.
- Artifact tests inspect every production module's JAR to exclude the endpoint, client, overlay and
  Spring readiness listener. Module-boundary checks exclude development dependencies transitively.
- The development command still needs to start this endpoint and provide current rendering IDs to the
  child. That composition belongs to #47, which depends on this issue.

## Follow-up

- [#47](https://github.com/christian-draeger/woge/issues/47): compose the Gradle dev command, build detail
  source, child development metadata and explicit CSP configuration.
- [#150](https://github.com/christian-draeger/woge/issues/150): preserve browser-owned state on refresh.
- [#153](https://github.com/christian-draeger/woge/issues/153): measure sustained development sessions and
  refine resource budgets before beta.

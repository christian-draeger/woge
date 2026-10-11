# ADR 0077: Define pre-1.0 compatibility and version-skew policy

- Status: Accepted
- Date: 2026-10-11
- Decision owners: @christian-draeger
- Related issues: [#118](https://github.com/christian-draeger/woge/issues/118), [#61](https://github.com/christian-draeger/woge/issues/61), [#62](https://github.com/christian-draeger/woge/issues/62)
- Builds on: [ADR 0013](0013-length-prefixed-patch-framing.md), [ADR 0036](0036-dual-fallback-client-distribution.md), [ADR 0047](0047-canonical-failure-and-recovery-model.md)

## Context

Woge is pre-1.0 and has no previous stable release to use as a compatibility baseline. A rolling
production deployment can still have browser assets from one node talking to another node. Gradle
modules, the npm browser package, generated KSP code and the Spring and Ktor adapters must therefore
have one practical alignment rule before a 1.0 promise is made.

## Decision

1. **Align artifacts.** All published JVM artifacts use the root `wogeVersion`. The browser runtime
   served from a Woge server artifact is the runtime bundled for that same release; the separately
   published npm package uses that release version too. There is no Woge BOM today, and we will not
   add one until applications need to choose Woge artifacts independently. Spring Boot's dependency
   platform remains the Spring dependency alignment mechanism.
2. **Negotiate patch and action responses.** Browser action requests send
   `Accept: application/vnd.woge.patch-stream; version=1`. A server that receives an explicit
   unsupported patch-stream version rejects it with `406 Not Acceptable`,
   `Woge-Protocol-Error: unsupported-version`, and `Cache-Control: no-store`, before action
   execution. Ordinary native form requests do not send this media type and keep their normal HTML
   behavior. The five-byte `WOGE` preamble and each patch metadata record also carry version 1.
   Unknown preamble or metadata versions are rejected as `WOGE_UNSUPPORTED_VERSION`; the browser
   cancels the stream and uses the bounded, once-per-URL-and-tab safe document reload. It never
   resends an action POST.
3. **Negotiate live updates through the URL.** EventSource cannot set an application `Accept`
   header, so the browser adds `_woge_protocol_version=1` to the live URL. Each adapter rejects an
   explicit unsupported value with the same 406 and `Woge-Protocol-Error` response before opening a
   subscription. EventSource hides the HTTP response details, so its error is surfaced to the
   application's `onError` callback as `WOGE_LIVE_CONNECTION`; it never changes the DOM. The v1
   `invalidate` and `resync` events carry region IDs and only request normal authorized GET refreshes.
   Unknown event names are ignored and existing event meaning/framing stays compatible within the
   adjacent-minor promise.
4. **Define the N-1 boundary.** During beta, adjacent minor releases (N-1 and N) interoperate for
   patch, action and live wire formats when they use the same supported protocol version. This is a
   wire promise, not a promise that JVM binaries or generated source compile unchanged. No N-1 binary
   compatibility is promised before 1.0. A client or server with an unsupported protocol version
   fails closed; actions receive the 406 response above and streams trigger the bounded reload path.
5. **Handle immutable browser assets safely.** A page on a newer node can refer to a fingerprinted
   `/_woge/assets/**` URL that an older node does not contain. That node returns 404. The HTML page
   and native forms still work without the optional JavaScript; do not rewrite the request to an
   unversioned asset or cache a 404 as immutable. Deployments must preserve old asset trees for the
   rollout window if enhanced behavior must continue across nodes.
6. **Use three API stability labels.** `Experimental` APIs are explicitly marked and may change
   without notice. `Beta` APIs are the default maximum for every pre-1.0 public API; source and wire
   shapes can change between releases, subject to the adjacent-minor wire rule above. `Stable` is
   reserved for APIs explicitly frozen at 1.0 or later. Binary-compatibility API dumps catch
   accidental JVM surface changes; they do not create a pre-1.0 binary guarantee. Generated code has
   the processor's stability and requires applications to regenerate after upgrades.
7. **Change schemas deliberately.** Additions are compatible only when old readers can ignore them
   and old writers remain accepted. Existing patch metadata is canonical and rejects unknown fields,
   so adding a patch field or changing a required field is breaking for v1 and requires a new
   protocol version and fixtures. Action form fields may be added only when the application decoder
   safely ignores absent and unknown optional fields; action responses follow patch-stream rules.
   Existing SSE event names, field meaning, line framing and ID interpretation are fixed for v1.

The [compatibility and upgrades guide](../guides/compatibility-and-upgrades.md) is the operator-facing
matrix. JVM protocol tests and browser tests consume the same v1 golden fixtures under
`modules/woge-protocol/src/test/resources/fixtures`; adapter TCK tests cover unsupported action
requests for MVC, WebFlux and Ktor.

## Alternatives considered

- **Promise N-1 binary compatibility now.** Rejected: no stable release exists, and it would freeze
  internal JVM details before the 1.0 surface is selected.
- **Let every module and browser package version independently.** Rejected: it makes mismatched
  wire implementations likely. A separate Woge BOM is unnecessary while all JVM artifacts share one
  root version.
- **Return an HTML response to an unsupported enhanced action.** Rejected: Fetch would reject the
  incompatible body after the action might already have run. A 406 before execution is explicit and
  cannot replay a mutation.
- **Use a custom version header on EventSource.** Rejected because the browser EventSource API does
  not let applications set request headers. A reserved query parameter provides explicit negotiation
  without changing application event payloads.

## Consequences

### Positive

- Browser/server skew has one bounded recovery behavior and never repeats a mutation.
- A rolling deployment can safely mix adjacent beta releases for the version-1 wire contract.
- The same fixture files make accidental wire changes visible in JVM and browser CI.
- Operators can keep HTML and native forms working even when an immutable asset is missing.

### Negative

- Beta users must align server artifacts, npm package and generated code themselves; no Woge BOM or
  binary compatibility guarantee is available yet.
- Strict v1 patch metadata means seemingly harmless fields require a protocol version change.
- A missing asset on an old node disables enhancement on that page unless deployments retain old
  hashed assets through rollout.

## Follow-up

- Apply the production compatibility policy and asset-retention guidance through [#61](https://github.com/christian-draeger/woge/issues/61).
- Use this policy as input to the final compatibility freeze in [#62](https://github.com/christian-draeger/woge/issues/62).
- Keep the adjacent-version matrix and shared fixtures in CI through [#118](https://github.com/christian-draeger/woge/issues/118).

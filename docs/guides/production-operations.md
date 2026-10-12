# Deploy and operate a Woge application

Deploy Woge like an ordinary Kotlin web server. Build a JVM application, put a reverse proxy in
front of it, and serve its CSS and optional browser modules at immutable URLs. Plain HTML/CSS
applications need neither Node nor a browser build tool. Spring MVC, Spring WebFlux and Ktor use
the same Woge wire contract; their server shutdown settings are different.

## A small executable deployment

With Java 21, Python 3 and a **running Docker engine**, run:

```sh
./gradlew productionDeploymentTest --console=plain
```

This builds the maintained [three-host reference application](../../examples/reference-application/README.md),
then runs real non-root JVM containers behind nginx. The
[Dockerfile](../../testing/deployment/Dockerfile),
[proxy settings](../../testing/deployment/nginx.conf) and
[verifier](../../testing/deployment/verify.py) are the complete deployment example. No cluster,
message broker, Node installation or browser is required. The task is opt-in; root `check` never
starts Docker. Missing Docker is a failure with an explicit blocker, not a skipped success.

The verifier starts two releases, saves the old HTML and the actual client modules, and moves new
traffic to the ready replacement. A stateless form from the retained HTML sends its original page
epoch and region revisions to the replacement. Its response is checked with a narrow version-1
reader first locked against the supported historical golden wire fixture. Native submission still
redirects. Unsupported action versions get 406, followed by a safe page GET, never automatic POST
replay. Both immutable asset trees stay accessible. An idle SSE stream on the old process flushes
heartbeats and drains (Spring) or terminates (Ktor/Netty) while the old container shuts down normally
(JVM SIGTERM may exit 143; SIGKILL/137 is not accepted).
A second stream exceeds the configured allowance and reports the existing typed rejection event.

Reports and container logs go to `build/issue61`. Each run owns uniquely named containers, an image
and a network and removes only those resources. HTTP attempts, subprocesses and stream reads have
deadlines. CI runs this focused task separately from browsers. The existing adapter TCK additionally
checks real socket resets, cancellation cleanup, cache policies, budget exhaustion and negotiation
on all three hosts. The existing proxy probe checks chunk arrival and a deliberately buffered,
compressed negative control; successful container startup alone does not prove streaming.

This tests a rollout of two release identities of the current reference build, plus historical v1
wire fixtures. It is **not** a claim that two arbitrary old/new application binaries, browser DOMs,
databases, TLS endpoints or CDNs were tested. Before upgrading a real application, run its old
release image against its new release and add its form/data migration journeys.

## Health, readiness and private diagnostics

| Host | Liveness | Readiness | Installation |
| --- | --- | --- | --- |
| Spring MVC and WebFlux | `/actuator/health/liveness` | `/actuator/health/readiness` | Boot Actuator; the reference `deployment` profile puts management HTTP on port 8081 |
| Ktor / Netty | `/health/live` | `/health/ready` | Example-owned routes enabled only with `WOGE_DEPLOYMENT=true` |

Liveness means the process can serve HTTP; readiness means it may receive new traffic. Do not make
liveness depend on a database outage, or all replicas may restart together. Add the application's
required dependencies to readiness; the stateless reference needs none. Ktor clears its example
readiness flag on `ApplicationStopping`; remove it from routing **before** sending SIGTERM rather
than waiting for that lifecycle notification.

The reference nginx denies management paths. Container ports are mapped only to local loopback
for verification; in production restrict management ports with network policy and authentication.
Health returns only a status, not configuration, credentials or exception details. Actuator exposes
only health: `env`, `configprops`, heap dumps and public slow-response diagnostics are not enabled.
Keep metrics and detailed diagnostics on a private, authenticated channel.

Use [Woge semantic observations](semantic-observability.md), not HTML or exception-message parsing.
Boot can export through its existing `ObservationRegistry`; Ktor passes a `WogeObserver` to its
handler factory. The reference maps `WogeOperationFinished` to safe operation/outcome/limit/threshold
logs. No inputs, bodies, cookies or secrets are logged. Use only bounded operation/outcome names as
metric labels; correlation IDs belong in secured traces. HTTP health is not a metrics endpoint.

## Rolling update and shutdown order

1. Upload the new immutable assets **without removing the old ones**.
2. Start the new server and verify readiness.
3. Move new requests to ready nodes. Keep existing streaming connections on the old node.
4. Send SIGTERM to the old process and allow its bounded requests/streams to drain.
5. After the deadline, terminate the remaining connections. Clients recover through safe GETs.
6. Keep old assets for the supported old-tab/cache window; rollback needs them too.

Spring's reference `application-deployment.properties` sets `server.shutdown=graceful` and
`spring.lifecycle.timeout-per-shutdown-phase=25s`. Boot stops admission and waits for active
requests. MVC additionally configures Woge's own `asyncTimeout=25.seconds`; a Spring MVC controller
timeout does not replace that Servlet timeout. WebFlux cancels response publishers on disconnect.
The Ktor entry point sets Netty's `shutdownGracePeriod=1000` and `shutdownTimeout=25000` milliseconds.
Unlike the measured Spring drain, Ktor/Netty can close an active SSE transport during shutdown before
its maximum lifetime. The verifier records this as `terminated`, not successful stream completion;
the client must reconnect/resync on a ready node. Do not promise identical socket drain behavior
across hosts merely because their Woge protocol is the same.
Both launchers use JVM PID 1 (`exec` in the container), so SIGTERM reaches the application.
Give your container orchestrator a termination deadline longer than host drain plus cleanup.

Do not wait indefinitely for SSE. Configure `LiveLimits.maxLifetime` below the drain deadline,
and its heartbeat below proxy idle-read timeout. The reference uses 20 seconds, a one-second
heartbeat and a 25-second host drain; the test shortens lifetime to four seconds. The shared runtime
owns this timer and releases the subscription, rather than introducing a second shutdown scheduler.
At a forced deadline a truncated patch response is **not** successful completion. Already applied
patches remain; a mutation may already have committed. Never replay its POST automatically.
MVC cannot detect every passive idle disconnect immediately: a failed write, async error or bounded
timeout ends its coroutine. Request-owned children must use cancellable I/O and `finally` cleanup.
See [streaming behind proxies](streaming-behind-proxies.md) for measured host differences.

## Compression and caching

Use the [verified streaming profile](streaming-behind-proxies.md): response buffering and
compression off for HTML streams, patches and SSE; `Cache-Control: no-store, no-transform` and
`X-Accel-Buffering: no` for patches/SSE. The reference disables origin compression, nginx gzip and
proxy buffering/cache. A browser missing `X-Accel-Buffering` does not prove buffering: nginx consumes
that header. Measure actual chunk arrival through your public CDN/TLS path before enabling
compression. Do not manufacture `Content-Length` or `Transfer-Encoding` for a stream.

Follow [HTTP caching](http-caching.md) and [production assets](production-assets.md):
personalized and streamed HTML are not shared-cache entries; conditional requests must not return
304 for a non-cacheable page. Explicitly cacheable public buffered pages may use their typed
validators and `Vary`. Static release URLs get immutable caching and conditional 304 responses.
Never return different bytes at an existing release URL. The probe retains the old module bytes,
checks their hash, and checks no-store HTML and immutable-asset revalidation after the switch.

## Resource allowances

Keep one handler factory per application: live admission belongs to that factory, not to the whole
cluster. The reference deployment passes the same existing policies to all three host factories:

| Environment setting | Reference default |
| --- | --- |
| `WOGE_MAX_REGIONS` / `WOGE_MAX_CONCURRENCY` | 64 / 4 deferred regions per request |
| `WOGE_PATCH_BYTES` / `WOGE_PATCH_COUNT` | 1048576 wire bytes / 64 patches per deferred response |
| `WOGE_LIVE_SUBSCRIPTIONS` / `WOGE_LIVE_SESSION_SUBSCRIPTIONS` | 200 per factory / 4 per supplied session key |
| `WOGE_LIVE_HEARTBEAT_SECONDS` / `WOGE_LIVE_LIFETIME_SECONDS` | 1 / 20 seconds |

Values must be positive; the existing `LiveLimits` also checks heartbeat/lifetime and session/global
relationships. Invalid configuration, including a missing shared identity secret, fails deployment
startup. The stateless probe supplies no session key,
so only its global live limit applies. The deployment page has a 64 KiB HTML allowance; action
builders, forms and uploads retain their existing request-owned defaults unless explicitly
overridden. These are not process-wide memory caps. Use ordinary container memory/CPU limits,
upstream deadlines and ingress connection/request limits as well.

See [resource budgets](resource-budgets.md) for byte/count accounting, per-action overrides,
413/429/503 responses and typed `context.exceededLimit` diagnostics. Exhaustion is visible as a
`REJECTED` semantic outcome; an incomplete stream never gets a successful terminal frame. Raising
server allowances requires reviewing browser allowances too. Earlier patches are not rolled back.

## Scale and recover old tabs

Share a stable, secret render identity across nodes (`WOGE_RENDER_IDENTITY_SECRET` in the reference,
Base64 of at least 32 random bytes). Store it in your secret manager, not the image or repository.
That keeps an old document's region targets meaningful on a new node. Identity is **not**
authentication; verify sessions, authorization and CSRF on every request. Do not rotate identity
mid-rollout without planning bounded page recovery.

Stateless HTML, deferred GETs and actions can use ordinary load balancing when nodes share
application data, sessions and render identity. Sticky routing alone does not preserve state when a
node disappears. The reference `/deployment` confirmation is deliberately stateless; its garden
notes and task-board demonstrations are in-memory demos, **not** a replicated production store.
Use a durable database for mutations and schema changes that old and new releases can both read.

Live connection counts and coalesced invalidations remain local. Choose explicitly:

- Route an SSE connection to any node and deliver changes through your existing shared event source.
  On reconnect authorize again and render authoritative current data, not missed HTML patches.
- Keep node-local live state with affinity if losing that state is acceptable; reconnect/resync must
  still work after node removal.
- Leave live enhancement off and use ordinary navigation or authorized refresh GETs.

Woge does not provide a distributed event broker, cluster quota or durable replay log. Retain both
asset trees and the same versioned wire meaning during adjacent-minor rollouts; read
[compatibility and upgrades](compatibility-and-upgrades.md) and the
[failure/recovery matrix](../architecture/failure-and-recovery.md). Malformed/exhausted streams fail
closed, superseded work is ignored, and incompatible versions get at most one safe page reload.
Do not gate a core deployment on experimental native DPU or optional WebSockets.

## Follow-up

Verify your actual old/new images, database migrations, session expiry, authentication, CDN/TLS
timing, HTTP/2 and chosen engines before promising production behavior. Keep image versions and
retained-asset policy current. Add application-specific readiness dependencies and private metrics
export through the existing observation adapter; this reference intentionally does not build a
gateway, scheduler or monitoring stack.

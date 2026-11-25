# Micronaut Todo spike

Run the Woge Todo page, generated filter, add action and deferred counts on an isolated Micronaut Netty server.
This is an opt-in JVM experiment for [#302](https://github.com/christian-draeger/woge/issues/302).

## Run the proof

Use JDK 21 and the local Maven repository from the fixed Woge pilot kit. Run from the Woge checkout
with this spike present. It compiles the existing portable Todo page, commands and store from
`examples/todo-spring-boot`. For an isolated copy, use
`-PwogeTodoSource=/absolute/path/to/todo-spring-boot`.

```sh
./gradlew -p spikes/micronaut-host -PwogeRepository=/absolute/path/to/pilot/repository clean check
./gradlew -p spikes/micronaut-host -PwogeRepository=/absolute/path/to/pilot/repository run
```

Open [the completed filter](http://127.0.0.1:8080/?filter=completed) and follow the ordinary
filter links. Open [deferred Todo counts](http://127.0.0.1:8080/deferred) for an HTML shell with
two independent regions. The shell links to the complete Todo list for visitors without JavaScript.
The server listens on loopback. For an intentional local write, restart it with a
fixture password supplied through the environment.

```sh
read -rs WOGE_SPIKE_WRITER_PASSWORD
export WOGE_SPIKE_WRITER_PASSWORD
./gradlew -p spikes/micronaut-host -PwogeRepository=/absolute/path/to/pilot/repository run
```

From a second terminal, submit a native form request. Enter the same fixture password when
`curl` prompts. A successful addition returns `303` and the canonical Location.

```sh
curl --include --user writer --header 'Origin: http://127.0.0.1:8080' \
  --data-urlencode 'title=From Micronaut' --data-urlencode 'filter=active' \
  http://127.0.0.1:8080/woge-actions/add-todo
```

Use `installDist` to build a standalone distribution. Use
`dependencies --configuration runtimeClasspath` to inspect its runtime closure.
Once Gradle has resolved dependencies, `--offline clean check installDist` repeats the build
and real-server tests. The packaged server reports its actual listener address after startup.

Run the installed app's browser proof with the repository's pinned Playwright dependency and Chromium.
The script starts a separate loopback instance with an ephemeral writer password and stops it afterwards.

```sh
npm --prefix client/woge-fallback-client ci --ignore-scripts
npm --prefix client/woge-fallback-client exec -- playwright install chromium
node spikes/micronaut-host/verify.mjs
```

## Verified inputs

| Part | Version and source |
| --- | --- |
| Woge | Fixed local `0.1.0-beta.1` artifacts built at `7bf1e3e53aa8f4332828eec9231882aae1aee275`. The unchanged Todo source at `ab4028eaf88df1d48523583abc2aa0e9e91cf3e4` matches that candidate. |
| Micronaut | Platform 4.10.25, resolved Core and HTTP server 4.10.37, Security 4.18.3 and Netty 4.2.20.Final. |
| Kotlin and build | Kotlin/serialization plugin 2.4.10, Woge KSP on 2.3.12, coroutines, its Reactor bridge and serialization runtime 1.11.0, Reactor 3.7.12, Gradle 8.14.4. |
| JVM and test environment | JDK 21.0.6 on macOS arm64 with a real Netty server on loopback. Installed browser proof used Node 24.13.1, Playwright 1.62.1 and Chromium 151.0.7922.34. |

Micronaut Java annotation processing and Woge Kotlin KSP run separately. The Micronaut platform
constrains the Micronaut and Netty libraries. The spike uses Woge's existing Kotlin versions.
The installed distribution includes the Woge core, SPI, protocol, server runtime and browser
assets. Micronaut's Jackson module provides the default JSON error response bean used by its
HTTP server. The installed distribution has 54 runtime JARs with no Spring, Ktor or test fixtures.
The portable page, command and store are the existing Todo sources. All Micronaut bindings,
authentication, threading and metadata translation live in this standalone build.

[Fixed Woge pilot artifacts](https://github.com/christian-draeger/woge/issues/50) ·
[Micronaut platform POM](https://repo.maven.apache.org/maven2/io/micronaut/platform/micronaut-platform/4.10.25/micronaut-platform-4.10.25.pom) ·
[Micronaut HTTP API sources](https://repo.maven.apache.org/maven2/io/micronaut/micronaut-http/4.10.37/micronaut-http-4.10.37-sources.jar) ·
[Micronaut Security sources](https://repo.maven.apache.org/maven2/io/micronaut/security/micronaut-security/4.18.3/micronaut-security-4.18.3-sources.jar)

## Bounded result

| Boundary | Check |
| --- | --- |
| GET, HEAD and assets | Valid filters return UTF-8 HTML and use the existing typed route. An unknown filter returns 400. HEAD returns equivalent content type with an empty body. The Todo CSS and Woge module each return 200. |
| Native add action | A real form POST uses the existing typed decoder and generated action. Its 303 leads to an ordinary GET showing one escaped row. An invalid title returns 400 with the original input and field error. |
| Enhanced add action | The same action returns version 1 patch media. The existing codec verifies three patches targeting the requested page epoch and a terminal frame. A fresh GET confirms one row in the fixture store. |
| Request security | Missing identity returns 401. The reader role, missing origin evidence and a foreign origin with forwarded headers return 403. Neither forwarded headers nor form data supply trusted host identity. A rejected request leaves the Todo store unchanged. |
| Transport bounds | An unsupported patch version returns 406. Micronaut caps the request at 16 KiB and the Woge form decoder retains its own field/body limits. Oversized requests with known and chunked lengths return 413. |
| Finalized metadata | A test-only GET and HEAD route round-trips repeated Woge response headers and a host-only `Secure`, `HttpOnly`, `SameSite=Strict` cookie with its path and lifetime. HEAD emits no body. The cookie is metadata evidence separate from the Basic fixture's identity. |
| Installed Chromium | JavaScript disabled exercises invalid input, original value and field association, native 303 and the full-list fallback. JavaScript enabled exercises a framed validation 400, error focus, a framed successful action on the same page epoch and both deferred regions. Each valid write appears once after reload. |

The Basic authentication provider is a loopback fixture. It reads separate passwords for the
`writer` and `reader` identities, and blank values disable their credentials. Micronaut supplies
the request principal. The spike copies the authenticated subject and roles into the Woge request
context and permits the write capability. The configured I/O executor performs the bounded body
read. The same-origin check uses the local Netty listener address and runs before decoding.

## Raw response streaming

Run the transport proof independently with the same pilot repository.

```sh
./gradlew -p spikes/micronaut-host -PwogeRepository=/absolute/path/to/pilot/repository \
  --offline test --tests '*StreamProofHttpTest'
```

A test-only route on the pinned Netty server returns a cold `Flux<byte[]>` as
`application/octet-stream`. A loopback HTTP/1.1 peer reads the exact first chunk
(`00 3c ff 0a`) while the remaining producer work is held. A separate GET completes
on the server's single Netty worker event loop. The held work runs on a Reactor
scheduler with one worker and a queue of one. Releasing it delivers the exact
second chunk (`05 80 00`) and a normal terminal chunk.

Two further peers close after the first chunk while the producer is held. One uses a TCP reset
and one closes the socket normally. Both cancel the publisher and clean up the worker before the
test releases it.

The slow-reader case reads one chunk, then stops draining a connection with a small receive
buffer, a requested 16 KiB send buffer and 4/8 KiB Netty write watermarks. The cold publisher
can supply 4,096 chunks of 8 KiB each. It stops below 256 while the channel is non-writable
and a second real GET still completes on the only event loop. Resetting the stalled socket fails
an observed Netty write promise and cancels the publisher. The producer stopped at 42 chunks in
four macOS runs and at 6 in the Linux control. Those buffer settings define this loopback demand
and cleanup test.

The cases have explicit HTTP, latching and cleanup deadlines. The fixtures live in the test source
set and are excluded from the installed spike.

## Woge deferred Todo document

`GET /deferred` uses a portable Woge page over the existing in-memory Todo store.
`InitialDocumentRuntime` renders the HTML shell and the original response carries
Woge's framed fallback patches. The spike renders UTF-8 through Woge's shared
per-frame renderer with one cumulative HTML budget per subscription. A cold
coroutine-to-Reactor bridge collects the document off Netty's event loop.

The real-server tests read the shell and patch preamble while both regions are
held. They release the completed region first, decode its patch while the active
region is still held, and check the page epoch from the original HTML. They
observe the request-scoped Woge trace and authenticated reader across suspension. The test route
reads a local file before opening the document on the Micronaut I/O executor. The deferred worker
reads the same file after suspension on its own bounded-parallelism dispatcher. Both thread probes
verify that none of that file work runs on Netty's event loop.

Woge's deferred admission limit rejects an oversized region set before any child or shell starts.
The host returns 503 with `no-store` and a body free of internal limit details. A controlled load
failure renders safe replacement text and the normal protocol terminator. A late patch-budget
failure omits the successful terminator.
A TCP reset after the shell cancels both suspended regions and runs their cleanup.
In Chromium, the installed spike applied both fallback patches. With JavaScript
disabled, the complete-list link opened the ordinary Todo page.

```sh
./gradlew -p spikes/micronaut-host -PwogeRepository=/absolute/path/to/pilot/repository \
  --offline test --tests '*DeferredTodoHttpTest'
```

## Contract disposition and decision

All 12 checks passed locally on macOS. The same compiled test classpath also passed all 12 on
Temurin 21.0.13+9 in a network-isolated Linux amd64 container emulated on the same machine.
The Linux run used Netty NIO and its own loopback stack. Installed browser verification ran on macOS.

The real-server checks reuse the portable application, the Woge action and deferred encoders,
limits and the patch decoder. They follow the matching cases of the [shared adapter TCK](../../testing/woge-adapter-tck)
within an independently built consumer.

| Shared surface | Spike coverage |
| --- | --- |
| Documents, routes, GET/HEAD, metadata, redirects, cookie and assets | Passed for the Todo and metadata probes. Conditional cache validation and asset retention across releases remain untested. |
| Typed native/enhanced add and validation | Passed through raw HTTP and installed Chromium, including media negotiation, error focus and a single visible write. Toggle, delete and durable replay remain outside the bound route. |
| Principal, authorization and CSRF | Passed for Micronaut Basic identity, writer/reader roles and Woge's trusted listener origin before mutation. Sessions, token-based CSRF and a trusted proxy configuration remain untested. |
| Bounds, cancellation and fallback patch transport | Passed for URL-encoded form limits, deferred admission and late failure, independent patch order, paused reader, peer reset/close and observed write failure. The add action remains buffered. |
| Full shared suite and additional transports | Unimplemented here. Native DPU, separate patch endpoints, SSE, WebSockets, streaming multipart, TLS and proxy/drain behavior are further adapter work. |

A controlled comparison in [#303](https://github.com/christian-draeger/woge/issues/303) is feasible
for the tested Micronaut 4.10 and JDK 21 journey. The evidence supports comparing that package
against equivalent independent hosts with pinned resources and the same outcomes. Any operational
or adoption advantage remains an open comparison question. Micronaut 5 and its Java 25 baseline require
an independently built and measured toolchain. The supported adapter decision remains governed
by the comparison, lifecycle/resource evidence and full correctness gates in #304.

[Kotlin Flow-to-Reactor bridge](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-reactor/kotlinx.coroutines.reactor/as-flux.html) ·
[Micronaut Netty source](https://repo.maven.apache.org/maven2/io/micronaut/micronaut-http-server-netty/4.10.37/micronaut-http-server-netty-4.10.37-sources.jar) ·
[Spike acceptance criteria](https://github.com/christian-draeger/woge/issues/302) ·
[Woge host boundary](../../docs/adr/0002-framework-neutral-host-boundary.md)

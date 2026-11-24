# Micronaut Todo action spike

Run the same Woge Todo page, generated filter and add action on an isolated Micronaut Netty server.
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
filter links. The server listens on loopback. For an intentional local write, restart it with a
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
and real-server tests.

## Verified inputs

| Part | Version and source |
| --- | --- |
| Woge | Fixed local `0.1.0-beta.1` artifacts built at `7bf1e3e53aa8f4332828eec9231882aae1aee275`. The unchanged Todo source at `ab4028eaf88df1d48523583abc2aa0e9e91cf3e4` matches that candidate. |
| Micronaut | Platform 4.10.25, resolved Core and HTTP server 4.10.37, Security 4.18.3 and Netty 4.2.20.Final. |
| Kotlin and build | Kotlin/serialization plugin 2.4.10, Woge KSP on 2.3.12, coroutines and serialization runtime 1.11.0, Gradle 8.14.4. |
| JVM and test environment | JDK 21.0.6 on macOS arm64 against a real Netty server on loopback. |

Micronaut Java annotation processing and Woge Kotlin KSP run separately. The Micronaut platform
constrains the Micronaut and Netty libraries. The spike uses Woge's existing Kotlin versions.
The installed distribution includes the Woge core, SPI, protocol, server runtime and browser
assets. Micronaut's Jackson module provides the default JSON error response bean used by its
HTTP server. Its classpath is independent of the Spring and Ktor hosts.

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

A second peer closes the socket with a TCP reset after the first chunk. The test
observes Reactor's cancellation signal and release of the running producer
before opening the work gate. Both cases have bounded waits. The fixture is in
the test source set and is excluded from the installed spike.

The Todo add mutation still serves buffered HTML and patch responses. The raw
stream proof is a Netty transport check. Woge deferred and live response wiring,
context across suspend/subscribe boundaries, cookie/session lifecycle, browser
enhancement, TLS/proxy behavior, other disconnect modes, sustained backpressure
and the full adapter TCK need further evidence.

[Micronaut Netty source](https://repo.maven.apache.org/maven2/io/micronaut/micronaut-http-server-netty/4.10.37/micronaut-http-server-netty-4.10.37-sources.jar) ·
[Spike acceptance criteria](https://github.com/christian-draeger/woge/issues/302) ·
[Woge host boundary](../../docs/adr/0002-framework-neutral-host-boundary.md)

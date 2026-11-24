# Micronaut read-only Todo spike

Run the same Woge Todo page and generated filter route on a standalone Micronaut Netty server.
This is an opt-in JVM experiment for [#302](https://github.com/christian-draeger/woge/issues/302).

## Run the proof

Use JDK 21 and the local Maven repository from the fixed Woge pilot kit. Run from the Woge checkout
with this spike present. The spike compiles the existing portable Todo page, commands and store
from `examples/todo-spring-boot`. For an isolated copy, override their location with
`-PwogeTodoSource=/absolute/path/to/todo-spring-boot`.

```sh
./gradlew -p spikes/micronaut-host -PwogeRepository=/absolute/path/to/pilot/repository clean check
./gradlew -p spikes/micronaut-host -PwogeRepository=/absolute/path/to/pilot/repository run
```

Open [the completed filter](http://127.0.0.1:8080/?filter=completed). Follow the ordinary filter
links to see the same HTML with JavaScript disabled. The shared page still renders the form
markup. This server registers the read route and serves the shared assets. Submit, live and
enhanced-action behavior remain outside this slice.

Use `installDist` to build a standalone distribution. Use `dependencies --configuration
runtimeClasspath` to inspect its runtime closure. Once Gradle caches the resolved dependencies,
`--offline clean check installDist` repeats the build and real-server test.

## Verified inputs

| Part | Version and source |
| --- | --- |
| Woge | Fixed local `0.1.0-beta.1` artifacts built at `7bf1e3e53aa8f4332828eec9231882aae1aee275`. Portable Todo source at `a6d1b27070b5cb6317f766d3c553dcb455f40161` matches the Todo source at that candidate. |
| Micronaut and server | Micronaut Platform 4.10.25, resolved Core and Netty HTTP server 4.10.37, Netty 4.2.20.Final. |
| Kotlin and build | Kotlin/serialization plugin 2.4.10, Woge KSP on 2.3.12, coroutines and serialization runtime 1.11.0, Gradle 8.14.4. |
| JVM and test environment | JDK 21.0.6 on macOS arm64 and a real Netty HTTP server on loopback. |

This combination keeps the Micronaut Java annotation processor separate from the Woge Kotlin
processor. It uses the Micronaut platform's Micronaut and Netty constraints with the existing
Woge Kotlin family. The installed runtime contains the Woge core, host SPI, protocol and browser
assets. The Gradle-installed image had zero Spring and Ktor libraries. Micronaut's runtime Jackson
module provides its standard JSON error response bean, which the server requires at startup even
for the HTML route. The spike uses the JDK 21 overlap and leaves Micronaut 5 and its separate
toolchain for a future decision.

[Fixed Woge pilot artifacts](https://github.com/christian-draeger/woge/issues/50) ·
[Micronaut platform POM](https://repo.maven.apache.org/maven2/io/micronaut/platform/micronaut-platform/4.10.25/micronaut-platform-4.10.25.pom) ·
[Micronaut HTTP API sources](https://repo.maven.apache.org/maven2/io/micronaut/micronaut-http/4.10.37/micronaut-http-4.10.37-sources.jar)

## Bounded result

| Boundary | Check |
| --- | --- |
| Plain page and typed route | Real GET returns 200 and UTF-8 HTML. Following the generated completed view link selects the correct filter. An unknown filter returns 400. |
| HEAD and assets | The same route exposes its HTML content type on a bodyless HEAD. The shared Todo CSS and Woge module each return 200. |
| Mutation | The Todo POST path is unbound and returns 404. |
| Application entry point | The installed distribution starts on loopback and serves the typed filter. |
| Threading and transport | The controller uses Micronaut's I/O executor and collects the fixed HTML document before handing a buffered response to Netty. |
| Pending | Authorization, trusted origins, CSRF, cookies, native writes, enhanced actions, deferred/live exchanges, backpressure, cancellation and request context across suspension. Full adapter TCK remains pending. |

The combination is feasible for a buffered read-only page. The next decision needs the same Todo
mutation through native and enhanced paths, with request security on the real server. Lifecycle
and parity evidence still gate performance comparison or any maintained adapter.
[Spike acceptance criteria](https://github.com/christian-draeger/woge/issues/302) ·
[Woge host boundary](../../docs/adr/0002-framework-neutral-host-boundary.md)

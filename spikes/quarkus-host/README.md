# Quarkus JVM host spike

Use JDK 21 and the Maven directory from Woge's fixed **0.1.0-beta.1** pilot at source
revision **7bf1e3e53aa8f4332828eec9231882aae1aee275**.

```sh
./gradlew -p spikes/quarkus-host -PwogeRepository=/absolute/path/to/pilot/repository test quarkusBuild
java -jar spikes/quarkus-host/build/quarkus-app/quarkus-run.jar
```

Open [the static page](http://127.0.0.1:8080/). The spike uses the unmodified Kotlin page and
stylesheet from the [complexity gradient](../../examples/complexity-gradient/README.md). GET
delivers complete HTML and HEAD returns its headers without producing the body. Quarkus serves the
stylesheet from the application archive.

Open [the forecast](http://127.0.0.1:8080/forecast) to run the unmodified portable deferred
page. The generated route binding serves the native full-page link and the shell's framed patch
stream. The package serves the example script and the pinned Woge browser assets locally.

```sh
npm --prefix client/woge-fallback-client ci --ignore-scripts
npm --prefix client/woge-fallback-client exec -- playwright install chromium
node spikes/quarkus-host/verify.mjs
```

The Chromium check launches the packaged JVM on a loopback port. It follows the full-page fallback
with JavaScript disabled and checks that the framed response replaces the loading state with
JavaScript enabled.

| Input | Verified version |
| --- | --- |
| Quarkus JVM platform | 3.40.1.2 LTS |
| Vert.x HTTP and coroutine integration | 4.5.35 |
| Kotlin and Gradle | 2.4.10 and 8.14.4 |
| Woge | Fixed local 0.1.0-beta.1 pilot artifacts |
| JVM | JDK 21 |

The independent HTTP tests exercise early Unicode bytes while remaining work is suspended, a second
request on the sole Netty event loop, status/header/cookie mapping, the original Woge trace after
suspension, stream termination on a late byte-budget failure, and producer cancellation after a
loopback TCP reset. The routes that control these probes are test fixtures.

Only anonymous page requests are bound here. The production follow-up needs authenticated context
mapping, typed actions, live integration, a slow-client and proxy study, the shared
adapter TCK, and a native-image test. The initial integration choice and current proof are recorded
in [ADR 0120](../../docs/adr/0120-probe-quarkus-vertx-http-host.md). [Issue #298](https://github.com/christian-draeger/woge/issues/298)

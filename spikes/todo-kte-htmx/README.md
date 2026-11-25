# Standalone KTE and htmx Todo baseline

This [#290 comparison fixture](https://github.com/christian-draeger/woge/issues/290) runs the add, validation, filter and live-update journeys on the same Spring Boot 4.1.1 WebFlux and Reactor Netty host as the [frozen #269 comparison](../todo-framework-comparison/README.md). The build consumes the maintained Todo repository and CSS directly, with the same ten seeded titles. Each JVM starts with a fresh in-memory board.

The Gradle plugin generates Kotlin from four KTE templates at build time. The application JAR loads those compiled classes with jte-runtime 3.2.5. htmx 2.0.11 is the npm `latest` stable line at execution. The build extracts its minified file from the pinned WebJar and serves it locally with the fixture adapter. [jte build and runtime setup](https://jte.gg/pre-compiling/) · [htmx documentation](https://htmx.org/docs/)

## Run and verify

Use JDK 21. For the optional browser check, use Node.js and the repository's pinned Playwright dependencies.

```sh
./gradlew -p spikes/todo-kte-htmx bootJar
java -jar spikes/todo-kte-htmx/build/libs/todo-kte-htmx.jar
```

Open `http://127.0.0.1:8080`. Add a Todo with either JavaScript setting, or submit spaces to see validation preserve the entered value. With JavaScript enabled, open another tab and type a draft while adding a Todo in the first tab. The second tab refreshes its board while keeping the draft.

```sh
npm --prefix client/woge-fallback-client ci --ignore-scripts
npm --prefix client/woge-fallback-client exec -- playwright install chromium
node spikes/todo-kte-htmx/verify.mjs
```

The verifier starts the built JAR on a dynamic loopback port and uses separate Chromium contexts for native and enhanced flows. It checks filtering, escaped titles, native 400/303, enhanced validation and focus, form/list/count/status updates, draft and selection during a peer update, a draft edited during a pending add, and foreign-origin denial.

## Application coordination

Native requests use regular GET and POST forms. The enhanced add uses htmx with an explicit form target, an in-flight synchronization rule and out-of-band board and status replacements. The adapter permits the named 400 validation fragment and focuses its summary. It retains a newer draft when the submitted form is replaced.

An EventSource invalidates the board for the current filter. The adapter keeps one active board GET and one pending refresh, aborts an older read at mutation start and waits for the mutation before applying current board HTML. The form is outside the live board. On a network failure, the app reports uncertain success and leaves the user to inspect current data.

## Local verification

On 2026-11-25, macOS arm64, JDK 21.0.6, Gradle 8.14.4, Node 24.13.1 and Chromium 151.0.7922.34, the packaged run passed the journeys above. The inspected application archive contained four compiled KTE template classes, 67 nested runtime JARs and 39,164,455 total archive bytes. Its nested render library was `jte-runtime-3.2.5.jar`. The packaged source assets were 52,182 bytes of htmx, 3,131 bytes of app coordination and 907 bytes of shared CSS. The htmx file matched the SHA-256 recorded for #269.

This delivery covers one same-host correctness slice. Further comparison work includes toggle and delete, deferred sections, network and reconnect failure tests, matched runtime and tooling measurements, and an independent implementation review. Rerun the verifier when the workload, renderer, host or htmx changes, and retain any new comparison measurements separately from #269.

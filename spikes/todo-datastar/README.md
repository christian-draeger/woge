# Standalone Datastar Todo baseline

A Spring Boot 4.1.1 WebFlux application with Thymeleaf 3.1.5.RELEASE and Datastar v1.0.5. Its executable JAR has its own runtime dependencies and assets. It loads the same Todo repository and CSS as the maintained [Woge Todo](../../examples/todo-spring-boot/README.md) and [KTE/htmx baseline](../todo-kte-htmx/README.md). All three start from the same ten active rows for the [packaged comparison](comparison-2026-11-25.md).

## Run and verify

Use JDK 21. From the repository root:

```sh
./gradlew -p spikes/todo-datastar bootJar
java -jar spikes/todo-datastar/build/libs/todo-datastar.jar
```

Open `http://127.0.0.1:8080`. The server binds to loopback by default and stores data in memory until restart. Try a spaces-only title with and without JavaScript. For live refresh, keep a draft in a second tab and submit a different title in the first.

```sh
npm --prefix client/woge-fallback-client ci --ignore-scripts
npm --prefix client/woge-fallback-client exec -- playwright install chromium
node spikes/todo-datastar/verify.mjs http://127.0.0.1:8080 datastar
```

Restart the server before rerunning the browser verifier. The verifier starts with ten rows and adds four, including one whose successful upstream response is deliberately dropped by Chromium. Both comparison baselines use this same browser verifier during a measured run.

## Request and browser behavior

The HTML links and form make ordinary GET and URL-encoded POST requests. Invalid input returns an HTML 400 with its literal value and associated field error. A successful native Add returns a 303 to the selected filter. The server also validates the method's form media type, single values and same-origin policy before mutation.

Datastar sends the established form as URL-encoded data, disables the submit button while it is in flight and disables mutation retries. The enhanced invalid response carries an explicit invalid signal and replacement error summary over SSE. Datastar v1.0.5 only consumes response patches on HTTP 200, so enhanced validation uses a semantic error payload with that status. The native 400 remains available. A successful patch clears the error and reports that the Add completed. [Datastar actions](https://data-star.dev/reference/actions), [SSE events](https://data-star.dev/reference/sse_events), [pinned fetch implementation](https://github.com/starfederation/datastar/blob/v1.0.5/library/src/plugins/actions/fetch.ts)

A subscription emits the current board version. A change triggers a GET for the selected filter, and Datastar morphs the resulting HTML. The form sits outside the board so refreshes retain its value, focus and selection. A later board read cancels an in-flight read for the same filter. The version uses the total row count for this Add-only fixture. A general mutable repository would need an independent revision counter.

The server generates a new CSP nonce for each page. The vendored script matches the [v1.0.5 release](https://github.com/starfederation/datastar/releases/tag/v1.0.5) at SHA-256 `7380e0344b7f937d80ad70ededa1c28578173157ee4748be1850991491d911fd`. The upstream MIT license is in the packaged resources. The application serves the module directly and needs no CDN or browser bundler. [Datastar CSP integration](https://data-star.dev/reference/security), [Thymeleaf escaping and templates](https://www.thymeleaf.org/doc/tutorials/3.1/usingthymeleaf.html)

The fixture and comparison are maintainer-authored. Rerun the browser verifier and record a new dated comparison when the workload, renderer, browser runtime or packaged dependency closure changes.

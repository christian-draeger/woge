# Three packaged WebFlux Todo implementations

This [#290 study](https://github.com/christian-draeger/woge/issues/290) compares equivalent visible Add, filter, validation and two-tab refresh tasks. Datastar, KTE/htmx and Woge run as three independent executable JARs on Spring Boot 4.1.1, Spring WebFlux 7.0.9 and Reactor Netty 1.3.7. The maintained Todo repository and CSS come from [#265](https://github.com/christian-draeger/woge/issues/265). The ten active, escaped title rows preserve the shape of the verified [#269 workload](../todo-framework-comparison/isolated-results.md). The versioned fixture is `add-only-escaped-active-ten-v1`. The [comparison contract](../../docs/performance/comparison-contract.md) also defines a wider Todo scenario with a mixed active/completed seed, completion and deletion.

The study is maintainer-authored. An independent specialist review was unavailable. The observations cover these exact implementations and settings. They can help a maintainer assess packaging and coordination choices for this slice.

## Parity and boundaries

| Aspect | Observed outcome |
| --- | --- |
| Native useful HTML and full-document filtering | Ten active rows, an empty completed view, a selected-filter indicator and native links in all three. |
| Native validation and Add | All three returned 400, with literal spaces and a field link, and 303 after a corrected Add, with the selected view and one escaped row after reload. |
| Enhanced validation and recovery | All preserved the input, showed a field-linked error summary and focused it. They cleared the errors when corrected. Woge and KTE/htmx delivered 400 validation patches, while Datastar delivered an explicit semantic error in a 200 SSE response. |
| Enhanced Add | All three updated the list, counts and status without navigating. KTE/htmx cleared the unchanged submitted value. Datastar and Woge retained it. Each retained a newer draft, its focus and selection during a held POST. |
| Two-tab update | A second page used the live update to see the inserted row and retained a focused draft and its selected range. The timed path covers a connected public session. Authenticated reconnect and a controlled interrupted live subscription remain outside the fixture. |
| Input and origin rejection | Invalid titles changed no state, and a direct cross-origin Add returned 403 and introduced no row. The application is an anonymous same-origin fixture. |
| Uncertain mutation | Chromium forwarded one action to the server, received a successful upstream response and reset the page's connection before delivery. An uncertainty alert appeared, the draft remained visible and the success status stayed empty. A safe reload showed the one committed row. The driver observed one POST and issued zero repeated activations. |
| D1 deferred shell and region | The three packaged Todo variants tested here render the board in the initial document. The [opt-in Todo D1 follow-up](deferred-comparison-2026-11-25.md) measures the same delayed secondary region across all three renderers with separate packages and samples. The [Woge project study](../../docs/performance/reference-project-deferred-2026-11-25.md) measures a different D1 fixture across hosts. |
| Wider workflow | Completion, deletion, mixed active/completed data, persistence, authentication, multi-instance state, security coverage beyond the stated form boundary and saturation need separate fixtures and measurements. |

Before the KTE transport-alert correction, the same controlled connection reset committed the row and the live board displayed it. The alert stayed empty. The original [#341 archive observation](../todo-kte-htmx/README.md) remains frozen. The remeasured package recognizes XMLHttpRequest status 0 in the existing htmx completion listener. [htmx 2.0.11 transport events](https://github.com/bigskysoftware/htmx/blob/v2.0.11/src/htmx.js#L4653-L4673)

## Application work and dependencies

| Contender | Build and runtime responsibilities | Application-owned interaction coordination |
| --- | --- | --- |
| Woge 0.1.0-beta.1 | Gradle Woge plugin and KSP generate route/action glue. The runtime uses the Woge WebFlux starter, server/HTML/protocol modules, a version-pinned browser asset and the maintained consumer's actuator dependencies. | Typed page, action and region metadata declare validation, feedback, input identity and region targets. Shared runtime implements eligible actions, error targeting, draft protection and the live transport. |
| KTE 3.2.5 and htmx 2.0.11 | The jte Gradle plugin compiles four KTE templates. The renderer loads them with jte-runtime. Build-time WebJar extraction supplies htmx locally. | The form uses an in-flight htmx rule and out-of-band board and status updates. A local JavaScript adapter permits 400 validation swaps, focuses the summary and restores a newer draft when a form is replaced. It also queues board reads around mutation, aborts stale reads and reports unknown outcomes. |
| Thymeleaf 3.1.5.RELEASE and Datastar v1.0.5 | Gradle packages three HTML templates and the pinned module. Thymeleaf renders those templates at runtime. The build needs the Kotlin and Spring Boot plugins. | HTML declares pending, invalid and version signals, a retry-free Add, a live subscription and board morphs. The server emits version and field/error updates. The form remains outside the refreshed board, and an upgraded request failure sets an uncertainty signal. |

All applications include their host, renderer, business fixture and its selected interaction features. The exact nested runtime artifacts are in the evidence manifest. Runtime lists exclude build plugins, compiler tooling, the browser test harness and both runtime images. The maintained Woge variant includes its actuator starter and metrics support, so the full-package difference incorporates both the framework and the example's operations dependencies.

## Measurement

The shared [packaged runner](../todo-next/study.py) supplies Linux arm64 containers, the pinned Temurin image, loopback port selection, readiness, cleanup, package manifests and Woge seed preparation. The deployed JVM was 21.0.13+9 with two CPUs and 768 MiB per app. The OS and Gradle caches were warm. Chromium 151.0.7922.34 ran on the same macOS arm64 machine in fresh native and enhanced contexts, without network or CPU throttling. Requests used local Docker forwarding with no reverse proxy or transport compression.

Each app first passed the browser gate. Three rotated rounds used independent containers. Each round retained 15 warmups, then 60 serial complete ten-row document responses over one HTTP connection. Each round ran the same browser journey once. Browser timings end at asserted visible results, including the initial document, native filtered full document, validation focus, enhanced Add and peer refresh. The enhanced Add timer includes filling the input and activating the button. It ends after the list, count, status and cleared-error assertions. The peer timer covers the same Add until the new row becomes visible in another session. JavaScript body bytes are the sum of the delivered initial modules with an empty browser cache. Process RSS is one reading after the complete browser journey. Startup includes image launch and a useful unseeded response for Woge; seeding the Woge package through its public form route is recorded separately.

Build samples ran `clean bootJar` using Gradle 8.14.4 and Temurin 21.0.6+10, with warmed dependency caches and daemons, offline and without Gradle build or configuration caches. They include output regeneration and packaging. First dependency download and published artifact setup remain unmeasured. The declared practical thresholds were 5 ms for per-round HTTP p95 and 50 ms for browser interactions. Three rounds establish descriptive observations.

## Results

All 12 packaged browser journeys passed, including the three preflights. All 540 measured document responses and 135 warmups passed their correctness checks. The timings below are in round order.

| Measurement | Woge | KTE/htmx | Thymeleaf/Datastar |
| --- | ---: | ---: | ---: |
| Executable JAR, bytes | 43,545,169 | 39,164,506 | 41,524,174 |
| Nested production JARs | 85 | 67 | 71 |
| Total bytes of nested JARs beyond the identical shared set | 4,431,166 | 52,853 | 2,424,631 |
| Initial HTML, body bytes | 12,084 | 2,568 | 3,365 |
| Initial scripts, body bytes | 67,105 | 55,452 | 35,673 |
| Process RSS after browser journey, MiB | 209.4 / 215.6 / 213.1 | 174.6 / 174.2 / 175.9 | 187.3 / 186.3 / 187.9 |
| Startup to useful HTML before Woge seeding, ms | 1,804 / 1,756 / 1,787 | 1,420 / 1,375 / 1,409 | 1,550 / 1,495 / 1,565 |
| Offline clean build, s | 3.28 / 4.25 / 3.26 | 1.76 / 1.74 / 1.72 | 9.00 / 1.99 / 1.64 |
| Warm GET document p50, ms | 2.21 / 2.09 / 2.28 | 1.05 / 1.09 / 1.11 | 1.72 / 1.48 / 1.64 |
| Warm GET document p95, ms | 3.82 / 3.68 / 3.57 | 1.43 / 1.63 / 1.63 | 2.71 / 2.07 / 2.35 |
| Initial native HTML visible, ms | 52.5 / 55.5 / 60.0 | 59.8 / 58.2 / 70.3 | 61.6 / 67.7 / 60.7 |
| Native filtered document visible, ms | 37.5 / 37.6 / 45.4 | 39.8 / 38.6 / 42.0 | 41.2 / 41.4 / 48.0 |
| Native Add and redirect visible, ms | 53.1 / 45.9 / 50.8 | 43.3 / 45.5 / 44.2 | 48.1 / 46.5 / 48.4 |
| Enhanced initial HTML and script loaded, ms | 48.8 / 47.5 / 48.7 | 34.4 / 37.2 / 34.9 | 32.7 / 33.2 / 33.2 |
| Enhanced validation summary focused, ms | 30.6 / 33.4 / 30.4 | 29.0 / 31.9 / 32.7 | 28.6 / 35.9 / 28.1 |
| Enhanced Add visible, ms | 53.0 / 28.9 / 61.2 | 29.0 / 32.6 / 29.4 | 56.9 / 55.2 / 55.8 |
| Peer updated after Add began, ms | 75.4 / 75.0 / 75.5 | 23.0 / 75.5 / 73.5 | 74.8 / 73.6 / 73.5 |

The three packages share 66 byte-identical nested JARs totaling 38,819,870 bytes. Woge-specific modules account for 1,116,806 bytes, including the browser assets JAR. Its additional actuator stack and histogram support account for 2,077,879 bytes. Thymeleaf and its four renderer dependencies account for the Datastar package's entire nested difference. KTE requires a single 52,853-byte runtime JAR and ships the compiled templates as application classes. Each delivers the same 907-byte CSS. The KTE app ships 3,270 bytes of JavaScript coordination alongside the 52,182-byte htmx runtime. Datastar keeps its coordination in serialized HTML attributes and server routes. Woge's initial HTML carries the framework's page, action and region metadata in addition to the Todo content.

KTE/htmx had the smallest packaged server and RSS in every round. Datastar delivered the smallest initial script payload and had a 9.00 s first build. The full-build logs retain this outlier. The observed ranged differences in HTTP p95 stayed below 5 ms. Browser action and peer results vary by round, and the peer sample at 23.0 ms makes a stable claim about that path premature. The study supports package and transfer trade-offs for this workload. Representative latency under production load remains a separate question.

## Reproduce and retain

Use JDK 21, Node and Docker with local Linux arm64 support. Install the pinned [Playwright client](../../client/woge-fallback-client/package.json) and its Chromium, and pull the [pinned JVM image](../todo-next/study.py). Supply the immutable `0.1.0-beta.1` Maven repository from the fixed pilot built from Woge revision `7bf1e3e`. Without the pilot, check out that revision in a separate directory and run `./gradlew publishScaffoldArtifacts -PwogeVersion=0.1.0-beta.1` there. Build each target once with dependency access to populate the Gradle downloads before running the offline study.

```sh
python3 spikes/todo-datastar/study.py \
    --woge-repository /path/to/pinned-pilot/repository \
    --output /path/to/new-todo-study-evidence
```

The [evidence archive](evidence/2026-11-25-todo-jvm.tar.gz) contains the manifest, artifact digests, exact per-JAR runtime closures, individual latency/correctness samples and build/browser/server logs. It also includes the before-and-after transport alert evidence. The source runner, app, assets and verification are checked in. A change in workload, rendering, host, Gradle or browser warrants a new evidence directory and a dated report. Preserve the historical [#269 comparison](../todo-framework-comparison/isolated-results.md) and the [#277 Next.js deployment study](../todo-next/README.md) with their original sources, eligibility and raw samples.

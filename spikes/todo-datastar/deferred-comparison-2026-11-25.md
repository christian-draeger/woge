# Deferred Todo summary across three WebFlux renderers

This [#290](https://github.com/christian-draeger/woge/issues/290) follow-up implements the [D1 contract](../../docs/performance/comparison-contract.md) with a single read-only secondary region. The useful shell contains a heading and a link to the Todo board. After a suspending 360 ms delay, the region reads the existing ten-row snapshot and reports the total. The same loader, HTML and CSS apply to the enhanced path and the ordinary link to a complete document. The delay is an explicit study control, independent of renderer.

Each independently built JAR uses the same Spring Boot WebFlux and Reactor Netty versions as the [Add and live comparison](comparison-2026-11-25.md). D1 declares an opt-in build flag and runtime profile. Its routes, injected failure and probe counters are study features. Production Todo users continue to use the ordinary route.

| Contender | Enhanced implementation |
| --- | --- |
| Woge 0.1.0-beta.1 | The immediate shell declares a KSP-generated region, using the same public read policy as the page. The document advertises the scoped patch URL, and a small module fetches it and applies the framed stream with Woge's public patch runtime. A typed failure renderer supplies safe copy. |
| KTE 3.2.5 and htmx 2.0.11 | The immediate shell asks htmx to GET the secondary section on load and replace its outer HTML. The route renders one KTE section when the shared read finishes. |
| Thymeleaf 3.1.5.RELEASE and Datastar v1.0.5 | The shell declares a Datastar GET on initialization. Its SSE response emits a Thymeleaf-rendered section, which Datastar morphs into the existing DOM. |

## Verification and measurement

In real Chromium, a mutation observer records when the shell, loading text and completed summary enter the DOM. Browser assertions also check that useful navigation and the final text are visible, and that enhanced completion occurs in the same document. The enhanced failure shows a summary-unavailable message and retains the native fallback link. Chromium without JavaScript follows the link to a complete HTML document and observes a usable HTTP 503 page when the read is forced to fail.

The HTTP probe separately closes the ongoing secondary GET with a TCP reset after observing the shared loader start. The probe requires one server-side worker cancellation, then fetches an independent, correct native page. Its timers record the full shell body and full native body. This separates cancellation from a successful subsequent retry by a reader.

Each contender first passes the same browser and HTTP gates. The study then rotates three rounds with one observed journey per contender per round. The environment is Linux arm64 containers on local macOS arm64, pinned Temurin 21.0.13+9, two CPUs, 768 MiB, direct loopback forwarding and host Chromium 151.0.7922.34. The practical difference threshold is 100 ms of enhanced completion for this 360 ms workload. Results describe the three exact packages and one region task. The study is maintainer-authored.

## Results

All three preflights and nine measured journeys passed. Each included a control connection reset observed as one server-side cancellation, an independent complete HTML response and the induced native and enhanced failure paths. Times below are milliseconds in round order.

| Outcome | Woge | KTE/htmx | Thymeleaf/Datastar |
| --- | ---: | ---: | ---: |
| HTTP shell body complete | 7.9 / 5.7 / 9.2 | 4.0 / 3.9 / 4.3 | 8.8 / 8.7 / 9.6 |
| Shell heading entered DOM | 9.4 / 9.5 / 15.1 | 9.1 / 7.3 / 15.1 | 10.0 / 15.3 / 9.4 |
| Enhanced summary entered DOM | 426.9 / 428.1 / 436.7 | 423.8 / 415.4 / 443.1 | 431.0 / 473.3 / 422.6 |
| Complete native HTML response | 367.6 / 366.3 / 369.7 | 364.0 / 365.2 / 372.1 | 373.2 / 364.7 / 364.8 |
| Native click to visible full summary | 408.7 / 414.6 / 403.8 | 399.8 / 402.2 / 413.2 | 416.5 / 406.2 / 399.6 |
| Enhanced failure message entered DOM | 383.4 / 401.1 / 386.8 | 386.8 / 391.1 / 382.3 | 379.2 / 410.7 / 406.7 |

The browser saw the shell before the delayed region in each run. The spread across all observed enhanced completions was 57.9 ms, below the declared 100 ms practical threshold. The artificial delay and the small set of rounds limit performance conclusions to this local task. Direct HTTP checks also confirmed that the default runtime profile returns 404 for the study route and that the actual enhanced wire responses carry the safe failure message in each renderer's own format.

## Reproduce

Use the [Add study prerequisites and fixed Maven repository](comparison-2026-11-25.md#reproduce-and-retain). Build the three opt-in archives separately with JDK 21. The runner starts them with the `todo-study-deferred` profile on independent loopback ports and cleans up its containers.

```sh
./gradlew -p examples/todo-spring-boot -PtodoDeferredStudy=true \
    -PwogeVersion=0.1.0-beta.1 -PwogeRepository=/path/to/pinned-pilot/repository clean bootJar
./gradlew -p spikes/todo-kte-htmx -PtodoDeferredStudy=true clean bootJar
./gradlew -p spikes/todo-datastar -PtodoDeferredStudy=true clean bootJar

python3 spikes/todo-datastar/study-deferred.py \
    --woge examples/todo-spring-boot/build/libs/woge-todo-0.1.0.jar \
    --kte spikes/todo-kte-htmx/build/libs/todo-kte-htmx.jar \
    --datastar spikes/todo-datastar/build/libs/todo-datastar.jar \
    --output /path/to/new-deferred-evidence
```

The [evidence archive](evidence/2026-11-25-todo-deferred.tar.gz) retains package identities and runtime closures, per-round browser and HTTP observations, preflights, startup logs and the exact source patch. Keep this D1 evidence separate from the original packaged Todo measurements and the [#293 scheduled-arrival study](../../docs/performance/reference-project-deferred-2026-11-25.md). New versions or region characteristics warrant a separately dated run.

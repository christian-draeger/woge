# M1 multi-host walking-skeleton baseline

Recorded on 2026-09-06 for the maintained reference application. Run both commands from the
repository root to reproduce the source measurements:

```shell
./spikes/spring-html-htmx-baseline/measure.sh
./examples/reference-application/measure.sh
```

These are structural measurements, not throughput claims. Generated code, tests, build files and
blank or comment-only lines are excluded. The M0 fixture already contains actions and SSE that the M1
walking skeleton does not, so total source lines are not a like-for-like feature comparison. Host
glue, duplicated route strings and selector-owned patch targets are the useful comparison here.

## Recorded M1 values

| Area | Nonblank application lines |
| --- | ---: |
| Shared Kotlin page, data and markup | 355 |
| Shared application CSS and JavaScript bootstrap | 107 |
| Spring MVC bootstrap and routes | 63 |
| Spring WebFlux bootstrap and routes | 59 |
| Ktor bootstrap and routes | 49 |
| Total across all three hosts | 633 |

The shared source has zero Spring, Servlet, Reactor or Ktor imports. Across all three launchers and
the shared page there are 12 occurrences of the application-owned `/projects/` prefix and zero
application-owned CSS/htmx selectors naming patch targets.

## Comparison with the M0 hand-written Spring fixture

| Measurement | M0 baseline | M1 Woge slice | Change |
| --- | ---: | ---: | ---: |
| Spring MVC host Kotlin | 208 | 63 | -69.7% |
| Spring WebFlux host Kotlin | 206 | 59 | -71.4% |
| Both Spring host layers | 414 | 122 | -70.5% |
| Route-prefix occurrences | 27 across two hosts | 12 across three hosts | -55.6% while adding Ktor |
| Application-owned patch-target selectors | 2 | 0 | removed |

Woge does not remove normal route declarations: each host still visibly owns its URLs and input
decoding. The reduction comes from sharing page execution, markup, deferred-region behavior,
metadata and patch semantics behind the host port instead of duplicating controllers and templates.

## Executable coverage

The release gate compiles and runs one shared application through:

- one real-server adapter TCK for Spring WebFlux, Spring MVC and Ktor;
- one real-server integration test per executable launcher;
- the same enhanced and no-JavaScript Playwright journeys on all three hosts in Chromium, Firefox
  and WebKit—18 host/browser/mode combinations.

This baseline does not yet claim production latency, allocation or throughput budgets. Those require
a fixed deployment environment, warm-up policy and representative action/live-update workload. The
current measurements establish the reproducible comparison point without turning workstation or CI
timing noise into a compatibility promise.

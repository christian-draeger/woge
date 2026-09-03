# Baseline evidence and recommendations

Recorded on 2026-09-03 for the source in this directory. Run `./measure.sh` to reproduce the source counts. Generated code, tests, build files and blank or comment-only lines are excluded.

## Source measurements

| Area | Nonblank application lines |
| --- | ---: |
| Shared Kotlin fixture | 116 |
| Shared HTML templates and CSS | 274 |
| Spring MVC host Kotlin | 163 |
| Spring WebFlux host Kotlin | 177 |
| Total | 730 |

The application contains 25 occurrences of the application-owned `/projects/` route prefix across host code and templates. The templates contain two htmx target-selector occurrences and two unique selectors: `#task-create` and `#project-summary`.

These are occurrence counts, not a claim that every string has a distinct semantic meaning. They measure how many places a route or patch-target refactor can touch in this small journey.

## Host observations

| Concern | Spring MVC | Spring WebFlux |
| --- | --- | --- |
| Deferred work | `Thread.sleep` represents blocking work and occupies a request thread | suspending `delay` releases the request thread |
| Form binding | form bodies can be read with individual `@RequestParam` arguments | form bodies require a model object; `@RequestParam` only covered query parameters in this spike |
| Empty required title | arrived as an empty string | initially failed with 400 before validation until an explicit form object supplied a default |
| Cancellation potential | blocking work needs explicit interruption/cooperation | coroutine cancellation can propagate through suspending work |
| Template and domain reuse | shared | shared |

The HTTP tests now require both hosts to return the same shell, full-page fallback, 422 validation response, redirect behavior and htmx multi-region response. Cancellation behavior is not yet asserted because the baseline has no cancellable data source.

## Streaming and enhancement gaps

- The immediate shell starts three independent htmx requests. It does not stream the shell and region patches through one response.
- A slow MVC region occupies a servlet thread. The equivalent WebFlux delay suspends, but both controllers duplicate the same routes and rendering decisions.
- htmx out-of-band swaps solve multi-region updates with string IDs and response-template conventions. Neither the compiler nor Spring verifies that the targets exist.
- SSE, reconnect IDs, stale-event rejection and live authorization are intentionally absent from this M0 baseline.
- htmx is loaded from a CDN for the shortest realistic setup. Production would need a local asset, dependency update policy and CSP decision.
- There is no client build, hydration, virtual DOM or Kotlin browser runtime.

## Fallback and accessibility gaps

- A no-JavaScript user must follow the explicit complete-page link before task status controls become visible. Native mutation forms then use Post/Redirect/Get correctly.
- Server validation is semantic and returns 422, but an enhanced form replacement does not move focus to the error summary automatically.
- Replacing the complete `aria-live` activity region may be more verbose than announcing one concise message.
- Page titles do not yet distinguish validation failures, and no manual screen-reader pass or automated accessibility audit has been recorded.
- Authentication, authorization and CSRF protection are absent. The baseline is not safe for deployment.

## Where Woge must be materially better

1. One typed page/action/region model must generate host routes, native form actions, enhanced URLs and patch targets. Application authors should not synchronize 25 string occurrences manually.
2. One typed command decoder and validation result must behave identically on MVC, WebFlux and Ktor, including empty values, dates, status codes and field errors.
3. The same framework-neutral application code must stream an immediate shell and independently completed regions. Blocking and reactive host details belong in adapters.
4. Multi-region updates must use typed region references and a transport-neutral patch model rather than hand-maintained OOB template flags and CSS selectors.
5. The adapter TCK must cover disconnect cancellation, backpressure, native redirects, enhanced responses and error parity.
6. No-JavaScript behavior, focus restoration and concise announcements must be defaults with explicit, testable escape hatches.

The baseline supports the planned ports-and-adapters boundary: domain and HTML are already shareable, while routing, request decoding, status handling, delays and cancellation remain host concerns. Woge should remove duplicated host ceremony without hiding standard HTTP and HTML concepts.

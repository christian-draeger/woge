# Pre-API AI-DX control — 2026-09-03

This is an architecture control, not a scored Woge consumer run. It applies corpus v0.1 to the hand-written Spring HTML + htmx baseline before Woge has a public API or published scaffold. Its purpose is to expose the ceremony and unsafe gaps Woge must remove. The first comparable human/model consumer run is [#93](https://github.com/christian-draeger/woge/issues/93).

## Environment

- Date: 2026-09-03
- Participant kind: maintainer-guided AI-assisted repository review
- Corpus: [`corpus-v0.1.md`](../corpus-v0.1.md)
- Starting point: [`spikes/spring-html-htmx-baseline`](../../../spikes/spring-html-htmx-baseline/README.md)
- Toolchain: repository Gradle wrapper; exact dependency versions in the baseline version catalog
- Hosts: Spring MVC and Spring WebFlux
- Command: `./gradlew test`
- Result: successful; 9 tests, 0 failures, 0 errors, 0 skipped

The run used repository documentation because no public consumer documentation exists yet. It therefore cannot measure external discoverability and is not compared across model families.

## Corpus coverage

| Task | Control result | Evidence or gap |
| --- | --- | --- |
| ADX-01 first page | Partial pass | Semantic page and missing-project handling exist; route/link strings remain application-owned |
| ADX-02 typed route | Fail | 27 `/projects/` occurrences have no generated typed descriptor or compile-safe rename path |
| ADX-03 form/validation | Partial pass | Native/htmx validation behavior is tested, but authentication and CSRF are absent |
| ADX-04 deferred region | Partial pass | Three requests complete independently; this is not one shell-first streamed response and cancellation lacks real disconnect tests |
| ADX-05 multi-target | Partial pass | htmx out-of-band updates work through two CSS target selectors; targets are not typed |
| ADX-06 live update | Partial pass | Both hosts emit a finite SSE sequence; authorization, reconnect, stale rejection and real disconnect cancellation are absent |
| ADX-07 compiler repair | Not runnable | No Woge descriptors, negative fixture or Woge diagnostic exists yet |
| ADX-08 styling | Partial pass | Plain CSS exists; modern CSS/Tailwind interchange and browser evidence are pending |

Reproduced source measurements are 116 shared Kotlin lines, 274 template/CSS lines, 208 MVC host Kotlin lines and 206 WebFlux host Kotlin lines. There are 27 route-string occurrences, two htmx target-selector occurrences and two unique target selectors.

## Metrics and limitations

- Compile/test pass rate: 9 of 9 existing deterministic tests pass.
- Unsafe behavior: two release-blocking categories are known—no authentication/authorization and no CSRF protection. Focus restoration and live-region verbosity also lack conformance evidence.
- Invented Woge APIs: not measurable because no Woge API was offered to the participant.
- Correction iterations: the baseline implementation recorded one host-parity correction when WebFlux form binding required an explicit form object instead of MVC-style individual request parameters. This was a runtime-contract discovery, not the future ADX-07 compile fixture.
- Unnecessary application code proxy: 414 host-specific Kotlin lines and 27 route-string occurrences for a small shared journey.
- Model comparison: intentionally absent; one repository-aware run cannot establish model reliability.

## Improvements required before API freeze

1. Generate one typed route/action/region vocabulary and make invalid cross-kind references fail at the application source location.
2. Publish one Spring Boot consumer scaffold and canonical task guide with no maintainer-only setup.
3. Make MVC, WebFlux and Ktor decode one typed form/action contract identically.
4. Provide deterministic negative compile fixtures with stable diagnostic identifiers and a minimal valid repair example.
5. Turn authorization, CSRF, no-JavaScript behavior, focus restoration, adapter parity and browser behavior into shared hard gates.
6. Remove application-owned transport controllers and string patch selectors without hiding ordinary HTML, CSS or HTTP.
7. Keep the actual consumer run separate from pull-request CI and record all environment/version inputs in [#93](https://github.com/christian-draeger/woge/issues/93).

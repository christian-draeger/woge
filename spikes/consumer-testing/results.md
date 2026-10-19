# Results: consumer testing and browser API comparison

Recorded 2026-10-20. This is a bounded functional comparison, not a speed or flakiness ranking.
The Todo application's domain/concurrency and HTTP/real-patch-codec tests pass independently.
The standalone published-alpha consumer also passed [CI](https://github.com/christian-draeger/woge/actions/runs/39340245722/job/118359708021).

| Path | Executed result | Diagnostics/artifacts | What the result establishes |
| --- | --- | --- | --- |
| skrape parser 1.3.0-alpha.2 + JDK HTTP | Native form/label, boolean presence workaround, 400/value/error link pass | Immediate `check` failure; no browser or screenshot | HTML assertions work without the bundled fetchers/HtmlUnit |
| Direct Playwright Java 1.63.0 from Kotlin | All 15 applicable journeys pass, three engines | Retrying upstream assertions; 6 context traces | Kotlin calls the current Java API directly |
| Existing Kotlin DSL source, Playwright updated to 1.63.0 | Same 15 journeys pass, three engines | Same assertion/trace APIs; 6 context traces | Browser lifetime DSL compiles without an API rewrite; raw browser access remains necessary |
| Node Playwright Test 1.63.0 | 15 pass, 3 deliberate native-live skips, three engines | Built-in HTML report and per-test traces | Current fixtures/projects retain the most complete repository workflow |

Journeys: add/complete/filter/delete with escaped text; server validation/value/error association
and enhanced focus; second-tab live update preserving a draft. Native live cases intentionally
require ordinary navigation and are not counted as enhanced passes. No retries were enabled.
One matrix run does not establish a flake rate or production host parity; the adapter TCK owns that.
The original published-alpha 1.62.1 consumer and the 1.63.0 experiment are separate passing runs.

## skrape{it}: useful scope and upstream work

Inspecting upstream `57b8f2b993b809ba94a6a3637ddc59ff3c0287b4` confirmed that `hasAttribute`
checks a nonblank value, missing boolean/empty attributes. [Upstream PR #265](https://github.com/skrapeit/skrape.it/pull/265)
switches to jsoup presence semantics and adds regression coverage. Full parser test + detekt passed:
269 tests executed, three existing skips. The released probe uses `attributeKeys.contains("required")`
until that fix is released. Presence is distinct from `aria-invalid="false"` truth semantics.

Generic form/label/error helpers belong in skrape{it}. Start with explicit `label[for]`, IDs,
`aria-describedby` token relationships, presence and value assertions, and bounded selected-subtree
diffs. These are HTML contracts, not a recreation of the browser accessibility-name algorithm.
Current selector failures identify the selector, but the probe's `check` assertions do not provide a
rich expected/actual DOM diff; no claim that this ergonomics work is already implemented.

The MockMvc extension is compiled against Spring 5.3/`javax.servlet`, so it is not a verified Boot 4/
Spring 7 adapter. The Ktor extension still accepts `TestApplicationResponse`/`TestApplicationCall`,
which is not the current `testApplication`/client `HttpResponse` surface. Migrate that adapter to
`HttpResponse` + `bodyAsText()` and reject non-HTML responses; retain the old API only in an explicitly
versioned legacy module. WebTestClient can supply a decoded response string to the independent parser;
a dedicated adapter must verify content type/status and avoid consuming a streaming body indefinitely.
These adapter migrations remain scoped upstream work, not dependencies of Woge's native test recipe.

The JDK client follows no redirects by default, exposing real 303/Location assertions. A consumer can
attach a `CookieManager` for sessions. Host transports own cookies/security/status; the parser owns
HTML. The maintained [HTTP test](../../examples/todo-spring-boot/src/test/kotlin/example/todo/TodoApplicationTest.kt)
already decodes enhanced bytes with Woge's real codec. Do not parse those bytes as an HTML document
or implement another patch interpreter in skrape{it}.

## Browser path recommendation

Keep Node Playwright Test as the repository default. Direct Java calls from Kotlin are a useful
optional JVM consumer path. Java has semantic role/label locators, retrying assertions and tracing;
[1.63 adds nullability annotations](https://playwright.dev/java/docs/release-notes).
Kotlin's `Options().apply { ... }` already handles most option-lambda needs. The existing DSL mainly
adds lifetime/scoping and CSS shorthand; the experiment uses its browser scope and the unwrapped
upstream browser/context for complete coverage. Its current top-level DSL-marker annotation also
produces a Kotlin warning because a marker belongs on receiver types.

A valuable small continuation would add context options/lifetime, a correctly marked receiver,
semantic locator conveniences and a JUnit fixture with trace-on-failure. Preserve raw upstream access.
Do not generate a parallel copy of every Playwright method or introduce coroutine dispatcher hops.
[Java's threading rule](https://playwright.dev/java/docs/multithreading) requires thread confinement;
parallel JUnit classes need separate Playwright instances. [JUnit integration](https://playwright.dev/java/docs/junit)
is still experimental. The executed JVM harness is sequential, not a JUnit parallelism benchmark.
Node's project isolation/reports are already exercised; multiple JVM contexts and explicit traces are
exercised, but JVM parallelism, deliberately broken trace assertions and repeated flakiness trials
remain unmeasured. No runner migration follows from this one functional pass.

No application npm/Vite build is required for Woge. Playwright Java still has a driver and downloaded
browser dependencies. Kotlin/JS is useful where an island actually shares Kotlin code; it provides no
demonstrated benefit for these server-owned journeys. [Vitest Browser Mode](https://vitest.dev/guide/browser/)
can test isolated island code in real browsers; it does not replace the full-stack native/form/live
matrix. Vite remains an asset/dev tool rather than a browser automation engine.

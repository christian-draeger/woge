# M1 compiler corpus and diagnostics

The [M1 API corpus](../../examples/m1-api-corpus/README.md) is a compact Gradle consumer compiled by
the normal root build. It complements the full reference application: the reference proves runtime
behavior, while the corpus makes imports, dependencies and compiler failure modes quick to inspect.

## Find a complete positive example

Use the machine-readable [Woge framework index](woge-framework-index.json) to locate the current
module, public concept, guide and canonical source. Its tests compare versions with Gradle, npm and
the patch codec; compare its module list with the enforced module manifest; and reject stale paths.

The three corpus sources cover the complete implemented M1 surface:

- [HTML, CSS and sinks](../../examples/m1-api-corpus/src/main/kotlin/dev/woge/examples/m1/HtmlCssAndSinks.kt)
- [Page, deferred regions and patch protocol](../../examples/m1-api-corpus/src/main/kotlin/dev/woge/examples/m1/ProtocolAndPage.kt)
- [Spring WebFlux, Spring MVC and Ktor adapters](../../examples/m1-api-corpus/src/main/kotlin/dev/woge/examples/m1/HostAdapters.kt)

These links are the source. Documentation does not keep a second almost-identical code copy.

## Repair an invalid shape

The negative fixtures compile with Kotlin's internal diagnostic names enabled. The harness requires
the source filename, expected and received concepts, and searchable diagnostic identifier.

| Fixture | Meaning | Minimal valid direction |
| --- | --- | --- |
| `WOGE-COMPILE-HTML-001` | A `String` cannot become raw HTML | Keep data in `text(...)`; only audited sanitizer output uses `raw(unsafeHtml(...))` |
| `WOGE-COMPILE-HTML-002` | A URL attribute does not accept a plain `String` | Use `applicationUrl(...)` or `externalUrl(...)` and pass that value to `url(...)` |
| `WOGE-COMPILE-HTML-003` | An unsafe conversion lacks an audit marker | Add `@OptIn(UnsafeWogeHtmlApi::class)` at the narrow reviewed boundary, never merely to silence a failure |
| `WOGE-COMPILE-PROTOCOL-001` | Page epoch and region ID were swapped | Construct `PatchTarget(PageEpoch, RegionTargetId)` with the two distinct types |
| `WOGE-COMPILE-PORT-001` | Portable code imports a server framework | Move request decoding to the Spring/Ktor adapter and pass an application input to `PageUseCase` |

Kotlin owns ordinary type-mismatch IDs such as `ARGUMENT_TYPE_MISMATCH`. Woge owns the stable
`WOGE-HTML-UNSAFE-001` opt-in message because that safety boundary belongs to the library. Future KSP
route, component, region and action diagnostics remain explicitly owned by issues #26, #27 and #28;
the M1 corpus does not invent placeholder generated APIs.

Run the deterministic compiler evidence with:

```shell
./gradlew :woge-m1-api-corpus:check
```

For an AI-DX run, provide the participant this page, the framework index, the public task text in
[corpus v0.1](corpus-v0.1.md) and a clean consumer checkout. No maintainer prompt, private source
search or hidden API list is required. M1 currently executes ADX-01, ADX-04 and the plain-CSS part of
ADX-08; tasks requiring typed generated interactions become executable with their owning M2 issues.

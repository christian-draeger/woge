# M1 API corpus

This small module compiles representative uses of every implemented M1 application-facing boundary.
Each source includes all imports and the build file declares the complete dependency context.

- `HtmlCssAndSinks.kt` covers the familiar HTML DSL, safe values, ordinary CSS strings and buffered
  or streamed output.
- `ProtocolAndPage.kt` covers the host-neutral page/deferred-region ports, Patch IR and stream codec.
- `HostAdapters.kt` binds that unchanged page to Spring WebFlux, Spring MVC and Ktor inputs.
- `src/test/resources/negative` contains code that must not compile. `CompilerDiagnosticsTest` invokes
  the pinned Kotlin compiler and verifies source-located diagnostic names plus expected/received
  concepts.

Run only this corpus from the repository root:

```shell
./gradlew :woge-m1-api-corpus:check
```

The corpus is implementation evidence, not another Woge runtime module. The maintained reference
application remains the executable end-to-end consumer.

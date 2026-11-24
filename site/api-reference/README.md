# Woge JVM API reference

Build the HTML reference from a Woge source checkout with JDK 21.

```sh
./gradlew -p site/api-reference dokkaGeneratePublicationHtml
```

Open `site/api-reference/build/dokka/html/index.html`. The reference covers the HTML and CSS
DSL, headless UI, host and protocol contracts, Spring MVC and WebFlux adapters, Ktor, and
Spring Boot autoconfiguration. Source links to tracked declarations identify the checkout commit. Generated HTML tag declarations
appear without repository source links. Use a clean checkout
of the intended release revision and its version when archiving a release reference.

This standalone build resolves the scanned JVM modules from the same checkout and labels the
reference with its `wogeVersion` from `gradle.properties`. The release build can supply the same
`-PwogeVersion` override as the corresponding artifact build. Gradle can use `--offline` once the
wrapper and dependencies have been cached.

Use the [documentation index](../../docs/README.md) for tasks and guides. The
[framework index](../../docs/ai-dx/woge-framework-index.json) records entry points and capability
status. Compile-time tooling, development integrations and the Gradle plugin are outside the
scope of this JVM API reference. [Kotlin Dokka](https://kotlinlang.org/docs/dokka-gradle.html)
generates the HTML.

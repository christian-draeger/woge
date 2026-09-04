# Documentation snippets

Use a source marker when a guide needs to include or refer to an executable file:

```html
<!-- snippet: examples/<example-name>/src/main/kotlin/dev/woge/example/Page.kt -->
```

`./gradlew validateDocumentation` verifies that every marked file exists. The example itself must be part of the Gradle build so normal compilation supplies the Kotlin check; a marker does not compile source by itself.

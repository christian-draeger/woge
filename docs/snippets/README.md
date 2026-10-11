# Documentation snippets

Use a source marker when a guide needs to include or refer to an executable file:

```html
<!-- snippet: examples/<example-name>/src/main/kotlin/dev/woge/example/Page.kt -->
```

`./gradlew validateDocumentation` verifies that every marked file exists. The example itself must be part of the Gradle build so normal compilation supplies the Kotlin check; a marker does not compile source by itself.

The five-minute page/action source is
[`Notes.kt`](../../examples/reference-application/shared/src/main/kotlin/dev/woge/example/notes/Notes.kt),
with complete imports. Its shared module runs KSP and Kotlin compilation in `check`; the three host
modules compile its real route bindings. `referenceBrowserSmoke` additionally verifies the tutorial's
native and enhanced HTTP/browser behavior on all hosts. Kotlin blocks in guides are excerpts; copy
from the linked complete source, not a partial block.

The same validation task parses the [documentation index](../ai-dx/woge-framework-index.json), checks
its local guide/source paths and compares Woge, Kotlin, host and browser versions with build inputs.

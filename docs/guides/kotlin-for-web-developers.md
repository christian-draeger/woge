# Kotlin for this web task

You do not need a Kotlin course before reading the Woge quickstart. This page translates the small
amount of syntax in the project page into concepts you already know from HTML, JavaScript and server
routes.

## Read declarations from left to right

This request value comes from the compiled
[`ProjectPage.kt`](../../examples/reference-application/shared/src/main/kotlin/dev/woge/example/project/ProjectPage.kt):

<!-- snippet: examples/reference-application/shared/src/main/kotlin/dev/woge/example/project/ProjectPage.kt -->

```kotlin
public data class ProjectPageInput(
    public val project: String,
    public val view: ProjectPageView = ProjectPageView.SHELL,
)
```

- `data class` is a typed value with named fields.
- `val` makes a read-only field. Use `var` only when a value must be reassigned.
- The type follows the name: `project: String`.
- `= ProjectPageView.SHELL` is a default argument.
- The trailing comma keeps multiline edits tidy; it does not create another value.

Callers can name arguments, which makes route decoding readable: `ProjectPageInput(project = slug,
view = ProjectPageView.COMPLETE)`.

## Treat HTML blocks as nested callbacks

Woge tag functions take a Kotlin lambda: a block of code passed to a function. Inside the block, the
current `HtmlWriter` is implicit, so nested markup stays close to nested HTML.

<!-- snippet: examples/reference-application/shared/src/main/kotlin/dev/woge/example/project/ProjectDocument.kt -->

```kotlin
header(attributes = { classes("site-header") }) {
    nav(attributes = { aria("label", "Primary") }) {
        a(attributes = { url("href", applicationUrl("/projects/${project.slug}")) }) {
            text("Projects")
        }
    }
}
```

The named `attributes = { ... }` block describes the start tag. The final block describes child
content. `${project.slug}` is string interpolation, like inserting a value into a JavaScript template
literal. `applicationUrl(...)` validates the completed relative URL; interpolation itself does not
percent-encode a path or query component.

The tag wrappers give completion for common elements. `element("project-card") { ... }` remains the
escape hatch for a custom or newly standardized element. `text(value)` is explicit because it escapes
markup characters; raw HTML needs a separate unsafe opt-in.

## Let `suspend` mark code that can wait

<!-- snippet: examples/reference-application/shared/src/main/kotlin/dev/woge/example/project/ProjectPage.kt -->

```kotlin
override suspend fun open(request: PageRequest<ProjectPageInput>): PageResult {
    val project = findProject(request.input.project)
        ?: return failure(FailureCategory.NOT_FOUND, request.context.correlationId)
    return htmlPage { renderProjectDocument(project, request.input.view) }
}
```

- `fun` declares a function.
- `suspend fun` can wait for asynchronous work without claiming one thread while it waits.
- `override` says the function fulfills an interface contract.
- `PageRequest<ProjectPageInput>` means a page request carrying that input type.
- `?: return` means “if the value on the left is absent, return the value on the right”. Kotlin writes
  an optional value as `ProjectSnapshot?`.

The fixed `PageResult` outcomes let the compiler distinguish an HTML document, redirect and safe
failure. An unknown project becomes a typed `NOT_FOUND` result that WebFlux maps to status 404.

## Interfaces form the application port

The colon after `ProjectPage` lists interfaces it implements:

<!-- snippet: examples/reference-application/shared/src/main/kotlin/dev/woge/example/project/ProjectPage.kt -->

```kotlin
public class ProjectPage :
    PageUseCase<ProjectPageInput>,
    DeferredRegionsUseCase<ProjectPageInput>
```

`PageUseCase` opens the immediate page. `DeferredRegionsUseCase` declares later region work. Neither
contains a Spring request, Reactor type or Ktor call. Spring code translates HTTP at the outside edge,
which is why another host adapter can call the same class.

## Use the compiler as the first feedback loop

The important boundaries are types rather than naming conventions:

- text and raw HTML are different values;
- application and external URLs are different values;
- complete stylesheets and declaration lists are different values;
- a `PageUseCase<ProjectPageInput>` cannot accidentally receive another page's input type.

Run `./gradlew check` after an edit. It compiles the canonical example, starts its real-server test,
checks the public ABI, formats Kotlin, runs static analysis and validates documentation links. The
same deterministic gate is intended for human-written and AI-generated Woge applications.

Return to the [Spring Boot quickstart](quickstart-spring-boot.md) or read
[Render safe HTML values](safe-html-values.md) for the context-specific output rules.

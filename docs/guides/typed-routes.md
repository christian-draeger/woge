# Typed page routes

A page URL such as `/projects/woge?view=complete` has two jobs: links must build it correctly, and the
server must read it back. Write the URL pattern once on the page input and let Woge generate both
directions. Links stay ordinary `href` and form `action` values; no JavaScript is needed.

## Declare the route on the page input

```kotlin
enum class ProjectPageView { SHELL, COMPLETE }

@WogeRoute("/projects/{project}")
data class ProjectPageInput(
    val project: String,
    val view: ProjectPageView? = null,
)
```

- `{project}` is a **path parameter**. The class needs a non-null property with the same name.
- Every other property is a **query parameter**. It must be nullable: a missing or empty value is
  `null`, and links leave `null` values out.
- Values can be `String`, `Int`, `Long`, `Boolean`, `java.util.UUID`, an enum, or a value class that
  wraps one of them, such as `@JvmInline value class ProjectId(val value: String)`.
- A page without parameters can use an object: `@WogeRoute("/") data object HomeInput`.

Woge's KSP processor runs during the normal build (the Woge Gradle plugin adds it when your build
applies `com.google.devtools.ksp`). It generates `ProjectPageRoute`: the class name without `Input`,
plus `Route`.

## Build links

```kotlin
a(attributes = { url("href", ProjectPageRoute.url(ProjectPageInput("woge"))) }) { text("Project") }
// href="/projects/woge"

ProjectPageRoute.url(ProjectPageInput("woge", ProjectPageView.COMPLETE))
// /projects/woge?view=complete
```

Values are percent-encoded. Enum constants use lowercase with dashes in URLs: `IN_PROGRESS` becomes
`in-progress`. A `<form method="get">` can submit the same value with
`RouteValues.format(ProjectPageView.COMPLETE)`.

## Register the route with your host

Your router still lists every path. The route gives it the pattern and reads the values:

```kotlin
// Spring WebFlux
coRouter { GET(ProjectPageRoute.path, handlers.page(projectPage, ProjectPageRoute)::handle) }

// Spring MVC
SimpleUrlHandlerMapping(mapOf(ProjectPageRoute.path to handlers.page(projectPage, ProjectPageRoute)), 0)

// Ktor
val page = handlers.page(projectPage, ProjectPageRoute)
routing { get(ProjectPageRoute.path) { page.handle(call) } }
```

`ProjectPageRoute` only fits a `PageUseCase<ProjectPageInput>`, so the compiler rejects a route that
is wired to the wrong page. Deferred-region streams can read the same values with
`ProjectPageRoute.webFluxInput()`, `springMvcInput()` or `ktorInput()`.

The older form, `handlers.page(projectPage, input)` with a hand-written decoder, still works for URLs
that do not fit a route.

## Wrong values in a URL

| Request | Response |
| --- | --- |
| A path value does not fit, e.g. `/tasks/abc` for an `Int` | `404 Not Found`: no such page |
| A query value does not fit, e.g. `?view=unknown` | `400 Bad Request` |
| No route matches the path | your host's normal 404 handling |

By default the response is the same bodyless failure that `failure(...)` produces. The received
value is never echoed or logged.

Generated route decoding has the same status rules before a deferred stream starts: an invalid
epoch/path value returns a bodyless 404, and an invalid query value returns a bodyless 400. No
deferred work starts and no successful patch stream is returned for invalid routing context.

## Configure not-found and error pages

Install `FailurePages` on your handlers to share error markup across pages without changing their
components:

```kotlin
val failurePages = FailurePages { failure ->
    when (failure.category) {
        FailureCategory.NOT_FOUND -> htmlFrame {
            h1 { text("Page not found") }
            p { text("Check the link and try again.") }
        }
        FailureCategory.INTERNAL -> htmlFrame {
            h1 { text("Something went wrong") }
            p { text("Reference: ${failure.correlationId.value}") }
        }
        else -> null
    }
}

val handlers = WogeWebFluxHandlers(failurePages = failurePages)
// The same option works on WogeSpringMvcHandlers and WogeKtorHandlers.
```

With the Spring Boot starter, declare your `FailurePages` as a `@Bean`. Woge passes it to the
auto-configured MVC or WebFlux handlers; you do not need to replace the handler factory.

The hook receives only safe failure metadata. It returns a Woge HTML frame or `null` to keep the
response bodyless. Woge keeps the original HTTP status (for example 404 or 500), sets the HTML content
type, and sends no body for HEAD requests. No JavaScript is needed.

This applies to invalid route values and controlled `failure(...)` page outcomes, including
`FailureCategory.INTERNAL`. Unexpected exceptions still use the host's existing error handling;
Woge does not expose their messages or turn them into successful pages. Renderer exceptions also
propagate through that handling rather than silently falling back.

An unmatched URL is still owned by your router. Configure its ordinary not-found handler separately:
Spring Boot's error handling for MVC, a final not-found route in WebFlux, or Ktor's `StatusPages`.
That handler can reuse the same `FailurePages` hook with a `PublicFailure`; it does not require
changes to a page component.

## Mistakes the build reports

The build fails with a located message, a rule ID and a valid example when a route is wrong:

| ID | Problem |
| --- | --- |
| `WOGE-ROUTE-001` | the path is not `/literal/{name}` syntax, or a name repeats |
| `WOGE-ROUTE-002` | the annotated type is not an object or a class with `val` constructor properties |
| `WOGE-ROUTE-003` | a `{name}` has no non-null property with that name |
| `WOGE-ROUTE-004` | a query property is not nullable |
| `WOGE-ROUTE-005` | a property type cannot appear in a URL |
| `WOGE-ROUTE-006` | the class, its constructor or a value type is private |
| `WOGE-ROUTE-007` | two classes generate the same route name |
| `WOGE-ROUTE-008` | two routes use the same pattern, such as `/projects/{id}` and `/projects/{key}` |

`/projects/new` and `/projects/{project}` may both exist: a literal segment is a different pattern.

The design is recorded in [ADR 0051](../adr/0051-typed-page-routes.md).

# Write a framework-neutral page use case

The host SPI is the small seam between your Woge application and a server such as Spring Boot or
Ktor. It is implemented now; the actual server adapters are still M1 work.

If you know controllers or route handlers, `PageUseCase<Input>` has the same job: it receives decoded
input and returns a response. The difference is that its types belong to Woge, so the same application
code can run on Spring MVC, Spring WebFlux and Ktor.

## Start with one HTML page

```kotlin
data class ProjectPageInput(val projectName: String)

val projectPage = PageUseCase<ProjectPageInput> { request ->
    htmlPage {
        element("main") {
            element("h1") {
                text(request.input.projectName)
            }
        }
    }
}
```

Kotlin reads this much like modern server-side template code:

- `ProjectPageInput` declares the values the route must decode;
- `request.input` is that typed value, not a map of strings;
- `text(...)` escapes application data for HTML;
- `htmlPage` returns `text/html; charset=UTF-8`.

Generated typed routes will create and connect the input in a later M1 issue. Until then, tests or
hand-written adapter code can construct `PageRequest` directly.

## Stream meaningful page parts

Use a cold Kotlin `Flow` when parts become ready at different times:

```kotlin
val dashboard = PageUseCase<DashboardInput> { request ->
    streamingHtmlPage(
        flow {
            emit(htmlFrame { renderShell(request.input) })
            emit(htmlFrame { renderProjectList(loadProjects()) })
        },
    )
}
```

A `Flow` is an asynchronous sequence. “Cold” means its code starts only when the server begins
collecting the response. Each `HtmlFrame` is rendered lazily through the normal safe HTML DSL and is a
visible flush boundary. Cancellation from a disconnect or timeout stops the coroutine; render and
write failures are not converted into partial success.

Keep each frame meaningful and reasonably bounded. A frame is not a network packet: proxies and TCP
may split or combine bytes differently.

## Read request facts, then authorize

`RequestContext` contains immutable snapshots such as method, language, request/correlation IDs,
headers, cookies, authentication and CSRF verification. It never contains a Servlet request,
`ServerWebExchange`, Ktor call or Spring Security context.

Authentication answers “who did the host authenticate?” It does not answer “may this person view or
change this project?” Keep that domain check visible:

```kotlin
val page = PageUseCase<ProjectPageInput> { request ->
    val principal =
        (request.context.authentication as? AuthenticationFacts.Authenticated)?.principal

    if (principal == null || !projectPolicy.mayView(principal.subject, request.input.projectId)) {
        failure(FailureCategory.FORBIDDEN, request.context.correlationId)
    } else {
        htmlPage { renderProject(request.input.projectId) }
    }
}
```

`CsrfVerification.VERIFIED` likewise says only that the adapter's CSRF policy succeeded. Missing or
invalid verification is rejected before the use case runs. Neither fact replaces domain
authorization.

## Set response metadata

Metadata is fixed before the first HTML frame is collected:

```kotlin
val metadata =
    ResponseMetadata(
        status = ResponseStatus.OK,
        headers = ResponseHeaders.of(httpHeader("cache-control", "no-store")),
        cookies = listOf(responseCookie("theme", "dark")),
    )

htmlPage(metadata) {
    renderSettings()
}
```

Content type, content length, redirect location and cookies cannot be set as arbitrary response
headers. Their typed APIs prevent conflicting metadata and response-splitting characters.

Response cookies are host-only, Secure, HttpOnly and SameSite=Lax by default. Relaxing those values is
an explicit constructor choice.

## Redirect or return a controlled failure

Application-local redirects are ordinary and accept Woge's validated relative URL:

```kotlin
redirect(applicationUrl("/projects/42"))
```

The default is HTTP 303, which clearly tells a browser to follow with GET after a form action. An
external redirect has no convenience overload: pass a validated `ExternalUrl` and an explicit
`ExternalRedirectPolicy` allowlist.

For an expected failure, return a safe category and the existing correlation ID:

```kotlin
failure(FailureCategory.NOT_FOUND, request.context.correlationId)
```

There is deliberately no public message, request payload or exception field. The future host adapters
will map this to the shared error response. Unexpected exceptions still propagate so the adapter can
handle them according to whether response output has started.

## What comes next

The shared adapter TCK will run one use case through an in-memory harness and real Spring MVC, Spring
WebFlux and Ktor servers. Spring Boot remains the primary setup path. Action and live-update ports are
added only when their typed descriptors and Patch IR can be tested end to end.

The current executable examples are the
[`woge-host-spi` tests](../../modules/woge-host-spi/src/test/kotlin/dev/woge/host/PageUseCaseTest.kt).

# Run a Woge page with Spring WebFlux

Woge keeps the page itself independent from Spring. A small WebFlux bootstrap connects that page to
normal functional routes; there is no application controller and no Reactor type in page code.

## Write shared application code

The same class can provide the immediate document and the deferred work requested by the browser:

```kotlin
data class ProjectInput(val project: String)

class ProjectPage :
    PageUseCase<ProjectInput>,
    DeferredRegionsUseCase<ProjectInput> {

    override suspend fun open(request: PageRequest<ProjectInput>): PageResult =
        htmlPage {
            element("main") {
                element("h1") { text("Project ${request.input.project}") }
                regionPlaceholder(summaryRegion(request), elementName = "section")
            }
        }

    override suspend fun regions(request: PageRequest<ProjectInput>): Iterable<DeferredRegion> =
        listOf(summaryRegion(request))
}
```

This file imports Woge host, HTML and protocol types only. Repository calls inside a deferred
region are suspending Kotlin functions; they do not return `Mono` or `Flux`.

The example assumes `summaryRegion` deterministically declares the same page-scoped target in both
requests. Signed page context and generated descriptors replace manual target reconstruction later.

## Connect functional routes

The WebFlux-only bootstrap owns path decoding and handlers:

```kotlin
val projectPage = ProjectPage()
val input = WebFluxPageInput { request ->
    ProjectInput(request.pathVariable("project"))
}
val pageHandler = WogeWebFluxPageHandler(projectPage, input)
val patchHandler = WogeWebFluxDeferredHandler(projectPage, input)

val routes = coRouter {
    GET("/projects/{project}", pageHandler::handle)
    GET("/projects/{project}/woge-patches", patchHandler::handle)
}
```

Navigation returns a complete `text/html; charset=UTF-8` response. The fallback browser module then
Fetches the second route as `application/vnd.woge.patch-stream; version=1`. Each completed region is
flushed and can become visible while slower work is still running.

The default context mapper intentionally supports safe page methods only and treats the request as
anonymous. For an authenticated application, provide a `WebFluxRequestContextFactory` that translates
the current Spring Security principal and verified CSRF decision into Woge's immutable security facts.
Domain authorization still happens inside `ProjectPage` for both routes.

## What failures mean today

A controlled `PageResult.Failure` maps to its Woge status before the response starts. A Spring
`ResponseStatusException` thrown by route decoding or application setup also remains a normal
pre-stream Spring error. Once bytes are committed, an exception ends the response and cancels its
coroutine work; it cannot change the existing status.

The canonical error/recovery taxonomy and security integration are explicit later contracts. Until
then, do not translate a failed unsafe request into an automatic retry.

See [ADR 0028](../adr/0028-functional-spring-webflux-adapter.md) for lifecycle and buffering tradeoffs
and [the deferred-region guide](deferred-regions.md) for region declaration details.

# Build the first Woge page with Spring Boot

This quickstart runs a real Spring Boot WebFlux application. The browser receives useful HTML first,
then three independent project regions. Normal CSS styles the page and a small ES module applies the
updates. A complete page remains available through an ordinary GET form when JavaScript is disabled.

Woge is pre-release. This repository currently uses Woge `0.1.0-SNAPSHOT`, Spring Boot `4.1.1` and a
JDK 21 toolchain while producing Java 17-compatible JVM bytecode.

## Run one command

From the repository root, run:

```shell
./gradlew :woge-reference-spring-webflux:bootRun
```

Open `http://localhost:8080/projects/woge`. Stop the server with <kbd>Ctrl</kbd>+<kbd>C</kbd>.

The command needs no Node.js installation. Gradle includes the maintained, unbundled Woge browser
module in this development example. A normal published application will consume a versioned browser
package through its own asset pipeline once that distribution contract ships.

## Read the page as normal web traffic

The browser-visible flow uses ordinary requests and responses:

1. `GET /projects/woge` returns status 200 and a standards-mode HTML document immediately.
2. The head loads `/assets/application.css` and `/assets/application.js` through normal `link` and
   `script type="module"` elements.
3. The HTML already contains navigation, the project heading, three semantic loading sections and a
   GET form for the complete page.
4. The ES module fetches `GET /projects/woge/woge-patches` and passes its byte stream to Woge's small
   browser adapter.
5. Summary, activity and task markup replace their matching section contents as server work finishes.

Open the browser Network panel to inspect all of this. Region IDs are opaque registry keys rather than
CSS selectors. Woge leaves each outer `section`, its classes and its accessibility attributes in place
while replacing the children.

The real-server test in
[`WogeQuickstartApplicationTest.kt`](../../examples/reference-application/spring-webflux/src/test/kotlin/dev/woge/example/WogeQuickstartApplicationTest.kt)
starts the same application on a random port and verifies the HTML, status codes, decoded patch frames,
full navigation and static assets in `./gradlew check`.

## Write familiar HTML

The canonical page uses tag functions named after HTML elements. This excerpt is compiled from the
shared example; the [complete document source](../../examples/reference-application/shared/src/main/kotlin/dev/woge/example/project/ProjectDocument.kt)
contains its imports and surrounding functions.

<!-- snippet: examples/reference-application/shared/src/main/kotlin/dev/woge/example/project/ProjectDocument.kt -->

```kotlin
internal fun HtmlWriter.renderProjectDocument(
    project: ProjectSnapshot,
    view: ProjectPageView,
) {
    doctype()
    html(attributes = { attribute("lang", "en") }) {
        renderHead(project, view)
        renderBody(project, view)
    }
}

private fun HtmlWriter.renderHead(
    project: ProjectSnapshot,
    view: ProjectPageView,
) {
    head {
        meta { attribute("charset", "utf-8") }
        metadata("viewport", "width=device-width, initial-scale=1")
        metadata("description", "A web-native Woge project page")
        metadata("woge-page-epoch", projectEpoch(project).value)
        title { text("${project.name} project · Woge quickstart") }
        stylesheet(applicationUrl("/assets/application.css"))
        if (view == ProjectPageView.SHELL) {
            moduleScript(applicationUrl("/assets/application.js"))
        }
    }
}
```

`text(...)` escapes data. `url("href", applicationUrl(...))` keeps URL handling explicit. Attributes,
classes, `data-*`, `aria-*`, custom elements and unknown future tags still use the generic writer where
needed. There is no virtual DOM or mobile-style widget vocabulary.

## Keep application code outside Spring

[`ProjectPage.kt`](../../examples/reference-application/shared/src/main/kotlin/dev/woge/example/project/ProjectPage.kt)
depends on Woge's host-neutral interfaces, not Spring, Reactor or a server request. It returns the first
HTML document and declares the independently completing regions.

<!-- snippet: examples/reference-application/shared/src/main/kotlin/dev/woge/example/project/ProjectPage.kt -->

```kotlin
public class ProjectPage :
    PageUseCase<ProjectPageInput>,
    DeferredRegionsUseCase<ProjectPageInput> {
    override suspend fun open(request: PageRequest<ProjectPageInput>): PageResult {
        val project = findProject(request.input.project)
            ?: return failure(FailureCategory.NOT_FOUND, request.context.correlationId)
        return htmlPage { renderProjectDocument(project, request.input.view) }
    }

    override suspend fun regions(request: PageRequest<ProjectPageInput>): Iterable<DeferredRegion> {
        val project = findProject(request.input.project) ?: return emptyList()
        return deferredRegions(project)
    }
}
```

This is the application-side port in a ports-and-adapters architecture. The same class already runs
through the [Spring MVC launcher](spring-mvc-adapter.md); issue #24 adds Ktor and the cross-host gate.

Spring-specific code stays in
[`ProjectRoutes.kt`](../../examples/reference-application/spring-webflux/src/main/kotlin/dev/woge/example/ProjectRoutes.kt).
It keeps paths, path variables, query parameters and HTTP methods visible:

<!-- snippet: examples/reference-application/spring-webflux/src/main/kotlin/dev/woge/example/ProjectRoutes.kt -->

```kotlin
return coRouter {
    GET("/projects/{project}", page::handle)
    GET("/projects/{project}/woge-patches", patches::handle)
}
```

The neutral Woge starter selects and configures the WebFlux adapter. It does not make Spring a core
dependency and does not bundle MVC and WebFlux together.

## See progressive enhancement fail safely

Disable JavaScript and reload `/projects/woge`. The server HTML still exposes the project identity,
navigation, headings, loading state and a normal form. Submit **Load the complete page instead**. The
browser navigates to `GET /projects/woge?view=complete`, and the server returns all three sections in
one HTML response without the patch module.

An unknown project returns 404. An unknown `view` query value returns 400. The patch endpoint returns
its versioned media type with `Cache-Control: no-store`. These remain ordinary HTTP outcomes; Woge
does not hide them behind a client state machine.

The example's [`application.js`](../../examples/reference-application/shared/src/main/resources/static/assets/application.js)
uses standard `fetch`, `ReadableStream` and ES modules. Its
[`application.css`](../../examples/reference-application/shared/src/main/resources/static/assets/application.css)
uses cascade layers, nesting, logical properties and a container query. Replace either with your normal
frontend toolchain; Woge does not require a CSS or JavaScript replacement.

Next, read [Kotlin for this web task](kotlin-for-web-developers.md). It explains only the syntax used
above. The [Spring Boot integration guide](spring-boot-starter.md) covers adapter selection and limits.

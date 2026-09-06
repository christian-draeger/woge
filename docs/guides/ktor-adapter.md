# Run a Woge page with Ktor

Woge keeps your page independent from Ktor. A small Ktor bootstrap connects it to normal routes. If
you already know HTTP and Ktor routing, those concepts remain the application model.

Spring Boot is the primary Woge quickstart. Use this adapter when Ktor is your host or when you want
to verify that application code stays independent from either server framework.

## Keep page code portable

Define the page against Woge's typed host boundary:

```kotlin
data class ProjectInput(val project: String)

class ProjectPage : PageUseCase<ProjectInput> {
    override suspend fun open(request: PageRequest<ProjectInput>): PageResult =
        htmlPage {
            element("main") {
                element("h1") { text("Project ${request.input.project}") }
            }
        }
}
```

This class has no `ApplicationCall`, server engine or Ktor plugin import. The compiler therefore
keeps accidental host coupling visible.

## Connect ordinary Ktor routes

Create handlers once while installing your application module:

```kotlin
fun Application.projectModule() {
    val projectPage = ProjectPage()
    val handlers = WogeKtorHandlers()
    val input = KtorPageInput<ProjectInput> { call ->
        ProjectInput(requireNotNull(call.parameters["project"]))
    }
    val page = handlers.page(projectPage, input)

    routing {
        get("/projects/{project}") { page.handle(call) }
        head("/projects/{project}") { page.handle(call) }
        staticResources("/assets", "static/assets")
    }
}
```

The route still owns its URL and input decoding. The handler maps the portable result to status,
headers, cookies and `text/html; charset=UTF-8`. Each Woge HTML frame is flushed before the next one
is requested.

If the page also implements `DeferredRegionsUseCase<ProjectInput>`, add the patch route:

```kotlin
val patches = handlers.deferred(projectPage, input)

routing {
    get("/projects/{project}/woge-patches") { patches.handle(call) }
}
```

The browser receives the same versioned patch stream as it does from either Spring adapter. No RPC
layer or Ktor serialization format sits between the browser and the web-native page contract.

## Add authentication explicitly

The default mapper supports safe GET, HEAD and OPTIONS requests. It copies ordinary headers, parsed
cookies and the preferred language, but deliberately excludes raw credentials and CSRF headers.

For an authenticated application, supply a `KtorRequestContextFactory` to `WogeKtorHandlers`. Read
the principal and verified security decisions from your installed Ktor plugins and translate only
the immutable facts that application authorization needs. Both the page and deferred-patch routes
must repeat the same authorization decision.

## Understand cancellation and failures

Ktor's response coroutine owns page collection and all deferred-region children. Cancelling that
coroutine or failing a channel write cancels unfinished region work. A peer disappearing while the
response is completely idle becomes observable on a later write; use protocol heartbeats only when
a future long-lived feature requires a bounded detection delay.

A typed `PageResult.Failure` returns its client-safe status without a body. An unexpected use-case
exception before streaming is logged and becomes a bodyless 500. After bytes have started, a failure
terminates the response because its status can no longer change.

Run the maintained example with:

```shell
./gradlew :woge-reference-ktor:run
```

Then open `http://localhost:8080/projects/woge`. The same `ProjectPage`, HTML, CSS and JavaScript are
also exercised by both Spring Boot launchers.

See [ADR 0033](../adr/0033-suspending-ktor-adapter.md) for the lifecycle and buffering tradeoffs.

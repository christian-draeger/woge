# Run a Woge page with Ktor

Woge keeps your page independent from Ktor. A small Ktor bootstrap connects it to normal routes. If
you already know HTTP and Ktor routing, those concepts remain the application model.

Spring Boot is the primary Woge quickstart; Ktor is a first-class alternative, not a different
application model. Run `./gradlew :woge-reference-ktor:run` and open `/notes` for the same
[page and native/enhanced form](quickstart-spring-boot.md). Its compiled bindings use
`handlers.page(NotesPage(), NotesRoute)` and `handlers.action(AddNoteAction, addNoteForm.ktorInput())`.
The same cross-host browser tests verify both paths.

## Keep page code portable

The maintained [NotesPage and AddNoteAction](../../examples/reference-application/shared/src/main/kotlin/dev/woge/example/notes/Notes.kt)
use Woge HTML and host types. They have no `ApplicationCall`, server engine or Ktor plugin import.
The compiler keeps accidental host coupling visible. Complete imports are in that compiled source.

<!-- snippet: examples/reference-application/shared/src/main/kotlin/dev/woge/example/notes/Notes.kt -->

## Connect ordinary Ktor routes

Create handlers once while installing your application module. This is the compiled launcher used by
the integration and browser tests:

<!-- snippet: examples/reference-application/ktor/src/main/kotlin/dev/woge/example/ktor/WogeKtorQuickstart.kt -->

```kotlin
val notes = handlers.page(NotesPage(), NotesRoute)
val addNote = handlers.action(AddNoteAction, addNoteForm.ktorInput())
```

Inside `routing`, bind `get(NotesRoute.path) { notes.handle(call) }` and
`post(AddNoteAction.path) { addNote.handle(call) }`. Ktor still owns the routing table.
The compiled launcher includes HEAD and normal static-resource serving. The handler maps results
to status, headers, cookies and HTML; no Ktor serialization format replaces HTTP.

The same application can add [deferred regions](deferred-regions.md) or
[one-route live regions](live-updates.md). The versioned browser patch stream is the same on Ktor
and both Spring hosts.

## Protect form actions and add authentication

The default mapper treats requests as anonymous. `handlers.action(...)` uses the built-in same-origin
CSRF check: a matching Origin is accepted, or, if Origin is missing or `null`,
`Sec-Fetch-Site: same-origin` is accepted. Cross-origin and unverifiable requests receive 403 before
form decoding. This does not authenticate users or replace domain authorization.

For an authenticated application or token-based CSRF, supply a `KtorRequestContextFactory` to the
action binding. Read verified security decisions from your installed Ktor plugins and translate only
the immutable facts the application needs. When TLS terminates at a trusted proxy, install Ktor's
`XForwardedHeaders` plugin so the request origin reflects the public scheme and host. Do not trust
forwarded headers from arbitrary clients.

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

Then open `http://localhost:8080/notes`. The same notes page, action, HTML and CSS are
also exercised by both Spring Boot launchers.

## Develop with live reload

Apply Gradle's `application` plugin together with `dev.woge.application`:

```kotlin
plugins {
    application
    id("org.jetbrains.kotlin.jvm")
    id("com.google.devtools.ksp")
    id("dev.woge.application")
}

application { mainClass = "example.ApplicationKt" }
```

Read the port from the `PORT` environment variable in your `main` function:

```kotlin
val port = System.getenv("PORT")?.toInt() ?: 8080
embeddedServer(Netty, port = port) { /* routes */ }.start(wait = true)
```

Then run `./gradlew wogeDev` (or `./gradlew wogeDev --port=9000`). After each save Woge compiles,
restarts the Ktor process and refreshes the browser. A compile error is shown in the terminal and the
browser while the last working version keeps serving. Ktor always gets a full process restart; Woge
does not use Ktor's own auto-reload.

If your app sends a strict `Content-Security-Policy`, allow the development origin in `script-src`
and `connect-src` while developing. It is the `http://127.0.0.1:<port>` origin of the development
client script in the page `head`. Spring does this automatically; Ktor has no hook for it.

See [ADR 0033](../adr/0033-suspending-ktor-adapter.md) for the lifecycle and buffering tradeoffs and
[ADR 0074](../adr/0074-ktor-development-restart-parity.md) for the development loop.

For deployment timing, disconnect cleanup and operational settings, see
[streaming behind proxies](streaming-behind-proxies.md).

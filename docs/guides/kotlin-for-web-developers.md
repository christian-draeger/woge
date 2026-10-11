# Kotlin for this web task

You do not need a Kotlin course before the [page/form quickstart](quickstart-spring-boot.md).
Read only the syntax used for the current task. All excerpts here come from the
[compiled notes source with complete imports](../../examples/reference-application/shared/src/main/kotlin/dev/woge/example/notes/Notes.kt).

<!-- snippet: examples/reference-application/shared/src/main/kotlin/dev/woge/example/notes/Notes.kt -->

## Name the form value

```kotlin
@Serializable
public data class AddNote(
    val text: String,
)
```

`data class` is a typed value with named fields. `val` is read-only; the type follows the name.
The trailing comma makes edits tidy. `@Serializable` asks the serialization compiler plugin to
supply a decoder. It does not publish an endpoint. `@WogeAction` generates a typed action reference;
the host still binds its POST explicitly.

## Treat HTML blocks as nested callbacks

```kotlin
label(attributes = { attribute("for", "note-text") }) { text("New note") }
```

A Kotlin lambda is a block passed to a function. `attributes = { ... }` describes the start tag;
the last block describes children. Inside an HTML block the current `HtmlWriter` is implicit, so
nesting follows HTML. `text(value)` escapes data. `element("project-card")` remains available for a
custom or newly standardized element.

## Read a server function

`fun` declares a function. `suspend fun` can wait for asynchronous work; it does not make blocking
I/O non-blocking. `command: AddNote` means the action receives the decoded form value.
`context: RequestContext` supplies host facts and rendering helpers; leave it out when unused.

`PageResult` describes an HTML response, redirect, update or safe failure. `return` finishes the
function. In the example, a blank/overlong note returns a bad-request result before saving.
`context.render.updates(...) { replace(...) }` describes updated HTML and a native redirect once.

`NotesPage : PageUseCase<NotesInput>` implements the page contract. `override suspend fun open(...)`
fulfills it; `PageRequest<NotesInput>` carries that typed input. `data object NotesInput` is the one
input value for a page with no path/query fields. Neither it nor the page needs a Spring/Ktor request.

## Add language detail when the web task needs it

- URL fields and default query values: [typed routes](typed-routes.md).
- Waiting for independent data: [deferred regions](deferred-regions.md).
- A sequence of change notices (`Flow`): [live regions](live-updates.md).
- Reusable HTML functions and native behavior: [UI primitives](ui-primitives.md).

Run the affected module's `compileKotlin` to check imports, types and generated references. Fix source
under `src`, not generated files; [troubleshooting](troubleshooting.md) covers KSP setup and regeneration.

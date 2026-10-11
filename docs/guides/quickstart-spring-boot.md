# Build a page and form in five minutes

Run a Spring Boot page that lists notes and accepts one form. Start with ordinary
POST/Redirect/GET, then enable JavaScript to update the same page. Both paths execute **the same
server action**. Woge is pronounced "VOH-guh" (`/ˈvoːɡə/`); the API uses ordinary web names.

## Run it

From this repository, with JDK 21:

```sh
./gradlew :woge-reference-spring-webflux:bootRun
```

Open `http://localhost:8080/notes`. Stop with Ctrl+C. First-time dependency downloads may take longer
than five minutes. No Node, Vite or Tailwind is needed. Woge is pre-release: this checkout uses
`0.1.0-SNAPSHOT`, Kotlin `2.4.10`, Spring Boot `4.1.1` and Java 17-compatible bytecode.

For your own project, start from the [Spring Boot scaffold](../../scaffolds/spring-boot/README.md).
Its idiomatic configuration is the neutral Woge starter plus **one** host: `woge-spring-webflux` and
Spring Boot's WebFlux starter, or `woge-spring-mvc` and its web starter. Boot supplies the handlers.
The scaffold applies KSP and the matching Woge processor; commands also need Kotlin's serialization
plugin. Add the version-matched `woge-fallback-client-assets` dependency for enhancement. This
[example build](../../examples/reference-application/shared/build.gradle.kts) already includes it.
Until artifacts are published, use the scaffold's documented local repository setup.

## First, submit without JavaScript

Disable JavaScript in browser developer tools, reload, type a note and press **Add note**:

1. `GET /notes` returns a complete HTML document with the current list and a labelled form.
2. The browser sends `POST /woge-actions/add-note` with an ordinary URL-encoded `text` field.
3. The host verifies same-origin evidence and decodes bounded form input. The action validates and
   saves the note, then the native response is **303 See Other**, pointing to `/notes`.
4. The browser follows with `GET /notes`. Refreshing this GET does not repeat the POST.

The demo is a public, shared, in-memory notebook. Restarting clears it; it is not a persistence or
private-account example.

## Read the action

The [complete compiled source, including imports](../../examples/reference-application/shared/src/main/kotlin/dev/woge/example/notes/Notes.kt)
contains the page, form, decoder and region functions. Excerpts below are not standalone files.

<!-- snippet: examples/reference-application/shared/src/main/kotlin/dev/woge/example/notes/Notes.kt -->

```kotlin
private const val MAX_NOTE_LENGTH = 120

@Serializable
public data class AddNote(
    val text: String,
)

public val addNoteForm: FormDecoder<AddNote> = FormDecoder(AddNote.serializer())

@WogeAction("add-note")
public suspend fun addNote(
    command: AddNote,
    context: RequestContext,
): PageResult {
    if (command.text.isBlank() ||
        command.text.length > MAX_NOTE_LENGTH
    ) {
        return failure(FailureCategory.BAD_REQUEST, context.correlationId)
    }
    noteStore.add(command.text.trim())
    return context.render.updates(NotesRoute.url(NotesInput)) {
        replace(NoteListRegion, noteStore.all())
        replace(NoteStatusRegion, "Note added")
    }
}
```

`data class` names the form fields. `@Serializable` supplies the decoder; `@WogeAction` generates
`AddNoteAction`, a typed reference and URL. `suspend fun` can wait for server work. The action uses
`context` here to prepare updates; leave that parameter out when an action does not need it.
`updates` describes success once: native requests receive the redirect, enhanced requests receive
region HTML. There is no second mutation implementation.

## Write a normal form and bind its POST

```kotlin
actionForm(AddNoteAction, attributes = {
    attribute("id", "note-form")
    data("woge-action", "")
    data("woge-status", "note-status")
    data("woge-alert", "note-alert")
    data("woge-failure-message", "Could not confirm the result. Reload to check your notes before submitting again.")
}) {
    label(attributes = { attribute("for", "note-text") }) { text("New note") }
    input(attributes = {
        attribute("id", "note-text")
        attribute("name", "text")
        boolean("required")
        attribute("maxlength", MAX_NOTE_LENGTH.toString())
    })
    button { text("Add note") }
}
```

`actionForm` writes an ordinary `form method="post"` with the generated action URL. The data
attributes opt it into enhancement; they do not replace native submission. The label, required field
and maximum length are HTML features. Server validation still runs when clients bypass them.

The [compiled Spring routes](../../examples/reference-application/spring-webflux/src/main/kotlin/dev/woge/example/ProjectRoutes.kt)
keep HTTP methods and URLs visible:

<!-- snippet: examples/reference-application/spring-webflux/src/main/kotlin/dev/woge/example/ProjectRoutes.kt -->

```kotlin
val page = handlers.page(NotesPage(), NotesRoute)
val action = handlers.action(AddNoteAction, addNoteForm.webFluxInput())
return coRouter {
    GET(NotesRoute.path, page::handle)
    POST(AddNoteAction.path, action::handle)
}
```

## Now enhance the same form

Enable JavaScript and reload. The page loads `/assets/woge/index.js` as an external ES module. Its
host-owned render metadata activates the shipped form client; no application JavaScript is needed.
Submit again and inspect Network: the same POST requests a typed patch response instead of a
redirect. Two regions change: the list and the empty `role="status"` message. The form remains in
place; the update does not move focus. Pressing Enter in the field keeps focus there. A failed module load leaves the native form working.

The page renders regions with `region(page.target(NoteListRegion), ...)`, where
`page = request.context.render`. `@WogeRegion` on an HTML function generates its descriptor.
You do not create protocol IDs, counters or secrets in page code. See the short
[mental model](mental-model.md) before adding [validation](typed-actions.md#render-native-field-errors),
[deferred regions](deferred-regions.md) or [live regions](live-updates.md).

## Safe defaults you can see

- `text(...)` escapes submitted text; URLs use `applicationUrl(...)`. Do not turn user input into raw
  markup. The example's strict [CSP](content-security-policy.md) allows same-origin external scripts
  and CSS, not inline scripts or event handlers.
- The default action binding rejects cross-origin or unverifiable POSTs with 403 **before decoding**.
  This is CSRF protection, not authentication. Private notes need host authentication and domain
  authorization on every request. See [token-based policies and trusted proxies](typed-actions.md#register-a-post-endpoint).
- An invalid command gets 400 and makes no change. HTML `required` catches the common empty-field
  case; [field-error rendering](typed-actions.md#render-native-field-errors) adds a useful native
  error page when a richer form needs one.
- The document has `lang`, a heading, a label, visible keyboard focus and initially empty status/alert
  elements. Enhancement announces success or failure without moving focus. Test keyboard and
  screen-reader behavior using the [accessibility checklist](accessibility.md).
- A lost POST response may hide a committed mutation. The client **never retries a POST**. Reload the
  safe GET to check the result before deliberately submitting again.
- Pages and action responses default to `Cache-Control: no-store`. Opt into ordinary ETag,
  Last-Modified and conditional GET/HEAD only when appropriate; [304 is standard HTTP](http-caching.md),
  not a Woge revision mechanism.

## Use your web stack

The [example stylesheet](../../examples/reference-application/shared/src/main/resources/static/assets/notes.css)
is plain CSS. Use normal classes, `data-*`, `aria-*`, custom elements, external scripts and your own
ES modules. [HTML escape hatches](html-elements.md) and [CSS/assets](css-and-assets.md) remain explicit.
[Tailwind](tailwind.md), [unstyled primitives](ui-primitives.md) and [islands](mental-model.md#optional-layers)
are separate, optional layers.

WebFlux suits suspending I/O; do not block its event loop. MVC is an equal Spring choice for Servlet
integrations or blocking libraries. See the [support constraints and evidence](../architecture/spring-support-model.md)
and [measured baseline](../performance/m1-multi-host-baseline.md), not a blanket speed claim.

Ktor is a first-class alternative with the **same NotesPage and AddNoteAction**:
`./gradlew :woge-reference-ktor:run`, then open `/notes`. MVC uses
`./gradlew :woge-reference-spring-mvc:bootRun`. Stop one host before starting another on port 8080.
The [Ktor guide](ktor-adapter.md) shows its route bindings.

The [cross-host browser tests](../../client/woge-fallback-client/reference-browser-tests)
verify this tutorial in Chromium, Firefox and WebKit, with and without JavaScript, including 303/GET,
no POST replay on refresh, focus, strict CSP and rejected commands. Sources compile in `./gradlew check`;
`./gradlew referenceBrowserSmoke` runs those browser journeys.

Next: [develop with wogeDev](development-loop.md), or [troubleshoot a request](troubleshooting.md).

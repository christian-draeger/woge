# Render independent page regions when their data is ready

A deferred region lets the browser receive useful page HTML while slower server work continues. The
loading state is ordinary HTML, and the final content uses the same safe HTML writer.

## Declare the region

```kotlin
val summary = deferredRegion(
    target = projectSummaryTarget,
    loading = {
        element("p") { text("Loading project summary…") }
    },
    onFailure = { failure ->
        patchHtml {
            element("p") {
                val message =
                    if (failure == DeferredRegionFailure.TIMED_OUT) {
                        "Summary timed out"
                    } else {
                        "Summary unavailable"
                    }
                text(message)
            }
        }
    },
    content = {
        val project = projectRepository.load()
        patchHtml {
            element("p") { text("${project.openTasks} open tasks") }
        }
    },
)
```

### Name the target

`projectSummaryTarget` says which element the result replaces. Mark the HTML function that renders
the region with `@WogeRegion`. The build then generates a small descriptor object for it:

```kotlin
@WogeRegion
internal fun HtmlWriter.projectSummary(project: Project) {
    element("p") { text("${project.openTasks} open tasks") }
}

// Generated: ProjectSummaryRegion. One PageIdentity per rendered page.
val page = PageIdentity(pageEpoch, renderIdentitySecret)
val projectSummaryTarget = ProjectSummaryRegion.target(page)
```

With a typed target, `deferredRegion` only needs the data. The descriptor renders it with your
function, so a summary target can never receive task-list HTML:

```kotlin
val summary = deferredRegion(
    target = projectSummaryTarget,
    loading = { element("p") { text("Loading project summary…") } },
    onFailure = { patchHtml { element("p") { text("Summary unavailable") } } },
    content = { projectRepository.load() },
)
```

Repeated regions belong to a component with a key that stays the same while the item exists, never
the list index:

```kotlin
@WogeComponent
class ProjectCard(@WogeKey val id: ProjectId)

@WogeRegion(component = ProjectCard::class)
internal fun HtmlWriter.cardSummary(project: Project) { /* … */ }

val cardTargets = projects.associate { project -> project.id to CardSummaryRegion.target(page, project.id) }
```

Typed regions need the KSP plugin and the Woge processor in your Gradle build:

```kotlin
plugins {
    id("com.google.devtools.ksp")
}

dependencies {
    ksp("dev.woge:woge-ksp:<version>")
}
```

The generated ID is a short opaque value such as `w1Qm9…`. Your database keys never appear in the
HTML. Reordering, adding or removing list items does not change the IDs of the other items.
Addressing the same region, or the same key, twice on one page stops with an error that names it.

If the build rejects a declaration, the message starts with a stable ID and shows the valid form:

| ID | Rule |
| --- | --- |
| `WOGE-REF-001` | A region is a top-level `HtmlWriter` extension function |
| `WOGE-REF-002` | It has exactly one input, no type parameters and is not `suspend` |
| `WOGE-REF-003` | It and its input types are not `private` |
| `WOGE-REF-004` | The `component` of a region is marked `@WogeComponent` |
| `WOGE-REF-005` | A component has at most one `@WogeKey` |
| `WOGE-REF-006` | A key is `String`, `Long`, `Int`, `UUID` or a value class around one of them |
| `WOGE-REF-007` | Two regions in one package do not generate the same descriptor name |
| `WOGE-REF-008` | Names use letters, digits, `_` and `.` and stay below 128 characters |

Without the processor you can still name targets by hand with
`page.root.region(IdentityName.of("project-summary"))` and
`page.root.component(IdentityName.of("ProjectCard"), IdentityKey.of(id)).children.region(...)`.

Load `renderIdentitySecret` (at least 32 random bytes) from your secret store and use the same value
on every server of one deployment. `RenderIdentitySecret.random()` is fine for tests.

### Run the work

`content` is a suspending Kotlin function: it can wait for database or network work without owning a
thread while it waits. Creating `summary` does not start that work.

The failure renderer receives only a safe category. It cannot accidentally put a database exception
or request value into the page.

## Put normal fallback HTML in the shell

```kotlin
htmlPage {
    element("main") {
        regionPlaceholder(summary, elementName = "section") {
            classes("project-summary")
            aria("label", "Project summary")
        }
    }
}
```

This initially renders HTML equivalent to:

```html
<section
  class="project-summary"
  aria-label="Project summary"
  data-woge-region="summary-1"
  data-woge-revision="0"
>
  <p>Loading project summary…</p>
</section>
```

The element name, CSS class and accessibility label belong to the application. Woge adds only its
opaque region ID and revision. The region ID is not a CSS selector and does not grant authorization.

Do not add `aria-live` merely because content is deferred. Whether loading, success or failure should
be announced depends on the interaction. Woge defines that shared policy before providing a generic
announcement API.

## Keep loading states accessible

The loading HTML is what screen-reader users get until the region arrives, so write a short, real
sentence such as "Loading recent orders…" instead of an empty box or a spinner without text.

Woge does not announce finished regions, does not set `aria-busy` and never moves focus when a region
is replaced. The new content simply appears where the loading text was. If your page needs to say
something, render your own `role="status"` element and fill it from a patch. See
[ADR 0048](../adr/0048-document-owned-accessibility-announcements.md) for which announcement fits
which situation.

## Execute inside the request lifetime

The shared server runtime collects declarations with `DeferredRegionExecutor`. Up to eight region
tasks run at once by default. Each active task has a thirty-second timeout, and applications or host
configuration can choose tighter values.

Results arrive in completion order. A fast region declared after a slow region can therefore update
the page first. A timeout or normal application exception becomes the region's controlled failure
content; it does not cancel unrelated siblings. Cancelling collection cancels active and waiting
children.

## Send completed regions to the browser

The stable-browser path uses two normal responses. Navigation first completes a `text/html` document
containing the placeholders. An external module then Fetches one page-scoped Woge patch stream. This
keeps the document valid HTML and works with strict Content Security Policy without inline scripts.

The shared runtime assigns the initial interaction sequence and one revision step to each update:

```kotlin
executor.execute(regions).encodeDeferredPatchStream { update ->
    requestPatchIds.nextFor(update.region.target)
}.collect { chunk ->
    hostResponse.writeAndFlush(chunk.bytes)
}
```

The patch-ID source and `writeAndFlush` operation shown here belong to the host adapter. The first
chunk is the stream preamble. It is sent before any region is awaited, so the status line and headers
reach the browser right away. Every following non-terminal chunk contains one complete patch frame;
the last chunk contains the completion frame. Network boundaries can split or combine those writes
without changing the wire protocol.

The browser applies the first frame while the Fetch response is still open, so a fast region declared
after a slow one becomes visible first. A no-JavaScript request must never be left permanently on
loading HTML; the host integration resolves required final content into the document or keeps a
complete normal navigation path.

## Test the patch stream

Spring MVC, Spring WebFlux and Ktor all send status and headers before the first region finishes.
A test can therefore hold every region back and still read the response headers. Use this order:

1. Gate the regions, for example with a `CompletableDeferred` the region `content` awaits.
2. Send the request and read status and headers. This returns without releasing the gate.
3. Release the gate.
4. Read the frames until the completion frame.

```kotlin
val gate = CompletableDeferred<Unit>()
// region content: gate.await(); patchHtml { ... }

val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
assertEquals(200, response.statusCode())
assertTrue(response.headers().firstValue("content-type").get().startsWith("application/vnd.woge.patch-stream"))

gate.complete(Unit)
val decoder = PatchStreamV1.decoder()
val events = decoder.feed(response.body().readAllBytes()).also { decoder.finish() }
assertEquals(PatchStreamEvent.Complete(2), events.last())
```

Do not release the gate inside a callback that runs only after the response is complete. That waits
on itself and the test hangs.

See [ADR 0026](../adr/0026-structured-deferred-region-execution.md) for lifecycle and ownership and
[ADR 0027](../adr/0027-fetch-deferred-patches-after-html-shell.md) for the two-response transport
contract.

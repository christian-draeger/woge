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

The patch-ID source and `writeAndFlush` operation shown here belong to the host adapter. Every
non-terminal chunk contains one complete patch frame; the last chunk contains the completion frame.
Network boundaries can split or combine those writes without changing the wire protocol.

The browser applies the first frame while the Fetch response is still open, so a fast region declared
after a slow one becomes visible first. A no-JavaScript request must never be left permanently on
loading HTML; the host integration resolves required final content into the document or keeps a
complete normal navigation path.

See [ADR 0026](../adr/0026-structured-deferred-region-execution.md) for lifecycle and ownership and
[ADR 0027](../adr/0027-fetch-deferred-patches-after-html-shell.md) for the two-response transport
contract.

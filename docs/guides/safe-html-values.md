# Render safe HTML values

Woge renders ordinary text and attributes as data, even when their contents look like markup. Use a
different API only when the browser will parse the value in a different context, such as a URL.

The API on this page is available from `woge-core`. The common `div`, `a` and `input` wrappers planned
for M1 are not implemented yet, so this guide shows the lower-level `element` and `voidElement`
fallbacks.

## Render text and attributes

The compiled example renders a project card with familiar HTML attributes:

<!-- snippet: modules/woge-core/src/test/kotlin/dev/woge/html/SafeHtmlValuesGuideTest.kt -->

```kotlin
val html = renderHtml {
    element(
        "article",
        attributes = {
            classes("project-card", "grid gap-3", "md:grid-cols-[1fr_auto]")
            data("project-id", "woge-7")
            aria("busy", "false")
            styles(declarations("container-type: inline-size;"))
            styles(declarations("--accent: oklch(62% 0.2 250);"))
        },
    ) {
        element("h2") { text("Woge <preview>") }
        element("a", attributes = { url("href", applicationUrl("/projects/woge-7")) }) {
            text("Open & inspect")
        }
    }
}
```

`text(...)` is the important default: the browser receives `&lt;preview&gt;` and displays
`Woge <preview>` as text. The value cannot create another element. Attributes are always quoted and
encoded separately.

The Kotlin block after `element(...)` is its child content. The `attributes = { ... }` block describes
the start tag. Named arguments make those two lambdas unambiguous for readers who are new to Kotlin.

## Keep HTML attribute semantics

HTML Boolean attributes use presence, not the strings `"true"` and `"false"`:

```kotlin
voidElement("input", attributes = { boolean("disabled", formIsLocked) })
```

When `formIsLocked` is false, Woge omits `disabled`. Enumerated attributes such as
`contenteditable="plaintext-only"` remain ordinary string attributes. ARIA values are strings too, so
use `aria("busy", "false")` rather than treating `aria-busy` as Boolean presence.

Repeated `classes(...)` or `styles(declarations(...))` calls append non-empty contributors in source
order. Woge does not interpret Tailwind utilities, CSS custom properties or new CSS syntax. This keeps
browser and toolchain behavior intact. The [CSS and asset guide](css-and-assets.md) covers complete
stylesheets, head assets, CSP and Subresource Integrity.

## Use a URL type for URL attributes

Pass a validated URL value instead of a string:

```kotlin
url("href", applicationUrl("/projects/woge-7?tab=activity"))
url("href", externalUrl("https://docs.example.org/woge"))
```

`applicationUrl` is for relative links within the application. `externalUrl` currently permits
`http`, `https`, `mailto` and `tel`. Both reject controls and ambiguous input. Encode dynamic path or
query components before composing the URL; HTML escaping is not URL percent-encoding.

Use `url(...)` for a future or custom URL attribute too. Known URL attributes cannot accidentally use
the ordinary `attribute(...)` path.

## Treat escape hatches as code-review boundaries

Raw HTML is intentionally noisy:

```kotlin
@OptIn(UnsafeWogeHtmlApi::class)
fun HtmlWriter.auditedContent(htmlFromApprovedSanitizer: String) {
    raw(unsafeHtml(htmlFromApprovedSanitizer))
}
```

The opt-in says that application code audited the source and the exact browser context. It does not
sanitize the string. Keep the conversion close to the sanitizer or trusted source; do not convert a
request, form field or database value merely to satisfy the compiler.

The same rule applies to `unsafeUrl`, `srcdoc` and `unsafeAttribute`. Generic script and style
raw-text paths remain unavailable. Use the focused `style(stylesheet(...))` and external
`moduleScript(...)` APIs described in the [CSS and asset guide](css-and-assets.md); Woge does not
offer a convenient inline-script string API.

Finally, `styles(declarations(...))` only keeps the value inside the quoted HTML attribute. Do not pass
untrusted CSS declarations: CSS can still reference external resources and has its own parsing rules.

See [ADR 0020](../adr/0020-context-specific-html-values.md) for the complete boundary and the
[threat model](../security/threat-model.md) for the wider browser/server assumptions.

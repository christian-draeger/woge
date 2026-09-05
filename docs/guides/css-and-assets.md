# Style pages and load assets

Woge uses normal browser CSS. You can write selectors, cascade layers, nesting, custom properties,
container queries and future syntax without waiting for Woge to add a Kotlin property for it.

The APIs on this page are available from `woge-core`. The examples use Woge's low-level HTML writer;
the planned convenience tags will build on the same contract.

## Prefer an external stylesheet

Put production CSS in an ordinary `.css` file and load it in the document head:

```kotlin
element("head") {
    stylesheet(applicationUrl("/assets/application.a1b2c3.css"))
}
```

The result is ordinary HTML:

```html
<link rel="stylesheet" href="/assets/application.a1b2c3.css">
```

Woge does not bundle or transform that file. Your existing CSS tooling, browser support policy,
cache headers and content hashing continue to work normally.

## Colocate a small stylesheet

Use `stylesheet(...)` when keeping a small page or component sheet beside Kotlin is clearer:

```kotlin
val projectCardCss = stylesheet(
    """
        @layer components {
          .project-card {
            container-type: inline-size;

            & > h2 { margin-inline: 1rem; }
          }
        }
    """.trimIndent(),
)

element("head") {
    style(projectCardCss, nonce = responseNonce)
}
```

In Kotlin, `val` declares a read-only value and triple quotes create a multiline string. IntelliJ can
recognize the factory argument as CSS and provide CSS highlighting, completion and inspections.
Woge preserves the source rather than maintaining a property allowlist.

`style(...)` rejects an HTML `</style` sequence before writing output. It does not make interpolated
CSS trustworthy. Do not place request parameters, database text or other untrusted data in CSS.
Prefer predefined classes, data attributes and carefully constrained custom-property values.

## Add classes and declarations

Classes stay ordinary strings, so plain CSS, generated CSS Module names and Tailwind utilities can
coexist:

```kotlin
element(
    "article",
    attributes = {
        classes("project-card", "ProjectCard_root__a1b2c", "grid gap-4 md:grid-cols-[1fr_auto]")
        styles(declarations("--accent: oklch(68% 0.18 35);"))
        styles(declarations("view-transition-name: project-card;"))
        data("state", "ready")
        aria("busy", "false")
    },
)
```

Repeated `classes(...)` and `styles(...)` calls merge non-empty contributions in source order. Woge
does not parse, reorder or deduplicate utilities. `declarations(...)` gives IntelliJ a synthetic CSS
rule for declaration-list assistance and prevents a complete `CssStylesheet` from crossing into a
`style` attribute accidentally.

Tailwind remains an optional build tool. There is no Tailwind type or runtime dependency in
`woge-core`; candidate discovery and production integration belong to the optional adapter tracked
in issue #77.

## Load other head assets

The common helpers retain standard HTML names:

```kotlin
element("head") {
    metadata("description", "Project overview")
    propertyMetadata("og:title", "Projects")
    preload(applicationUrl("/assets/project-card.css"), asType = "style", mimeType = "text/css")
    moduleScript(applicationUrl("/assets/application.js"), nonce = responseNonce)
    assetLink("icon", applicationUrl("/favicon.svg")) {
        attribute("sizes", "any")
    }
}
```

`assetLink(...)` is the standards-shaped escape hatch for link relations that Woge does not yet know.
All asset URLs use `applicationUrl(...)` or `externalUrl(...)`, so URL validation and HTML attribute
escaping happen before bytes are written.

For a cross-origin asset with Subresource Integrity, supply the CORS mode explicitly:

```kotlin
stylesheet(
    externalUrl("https://cdn.example/theme.css"),
    integrity = subresourceIntegrity("sha384-YWJjMTIzNDU2Nzg5MA=="),
    crossOrigin = CrossOrigin.ANONYMOUS,
)
```

`cspNonce(...)` validates the shape of a nonce but cannot generate or prove its randomness. Generate a
fresh cryptographically random nonce per response and send the matching Content-Security-Policy
header. Prefer external same-origin assets for caching and a strict CSP; inline styles are an explicit
application policy choice.

## Keep patch behavior predictable

The fallback runtime preserves the classes and other attributes of a targeted outer region. Incoming
replacement markup keeps its own classes, inline declarations and custom elements, including CSS
Module names and Tailwind utilities. Custom elements receive their normal browser lifecycle callbacks.

Patch fragments reject executable and head content such as `script`, `style`, `link`, `meta` and
`iframe`. Load page assets deterministically in the initial document head; do not inject styles from
a streamed patch. Optional component scoping remains future build-time work, not a runtime styling
mechanism.

See [CSS authoring](../architecture/css-authoring.md) for the full design and
[Render safe HTML values](safe-html-values.md) for the surrounding HTML contexts.

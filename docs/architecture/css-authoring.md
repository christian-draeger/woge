# CSS authoring

Woge treats CSS as a browser language, not as a Kotlin widget API. If you already know selectors, the cascade, custom properties, media queries or container queries, that knowledge transfers directly.

This page records the accepted contract. The stylesheet and declaration-list types plus the head
asset helpers are implemented in `woge-core`. The optional scoped compiler remains a prototype and
is not a released API.

## Start with a stylesheet

An external `.css` file is the default production path:

```html
<link rel="stylesheet" href="/assets/application.a1b2c3.css">
```

The browser fetches and caches it normally. Woge does not translate the file, require a property DSL or decide which CSS features you may use. Browser support remains a normal product decision guided by the [compatibility policy](browser-support-policy.md).

When colocating a small sheet with Kotlin is clearer, the helper accepts ordinary CSS text:

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
```

For a `style` attribute, a separate declaration-list type prevents accidentally putting a complete selector in an HTML attribute:

```kotlin
val position = declarations("view-transition-name: project-card;")
```

Both inputs are strings on purpose. The JetBrains language-injection annotation lets IntelliJ treat the first as a stylesheet and wraps the second in a synthetic selector for CSS assistance. The annotation is a compile-only developer aid, not a browser or runtime dependency.

Render them only in their matching browser context:

```kotlin
style(projectCardCss, nonce = responseNonce)

element("article", attributes = {
    classes("project-card", "ProjectCard_root__a1b2c", "grid gap-4")
    styles(position)
})
```

The separate types make complete stylesheets and declaration lists difficult to confuse. Woge
checks the HTML boundary, but deliberately does not parse CSS properties or reject unknown syntax.

## Interpolation is still code

Kotlin templates make fixed values convenient:

```kotlin
val duration = "120ms"
val css = stylesheet(".notice { transition-duration: $duration; }")
```

The IDE can understand the static CSS around `$duration`, but neither the Kotlin compiler nor CSS tooling can validate an arbitrary value produced at runtime. Prefer static rules plus custom properties, classes or data attributes. Use small typed value helpers only where they enforce a real boundary. Never interpolate untrusted input into CSS source.

## Optional component scoping

A future opt-in compiler may colocate component CSS and rewrite selectors at build time. Its contract is intentionally similar to normal CSS:

- a stable qualified component identity produces a stable scope attribute;
- the server writes that attribute during ordinary SSR and every later patch;
- local selectors receive a zero-specificity `:where([data-woge-scope=...])` guard;
- `:global(...)` is the explicit escape for an application or third-party selector;
- local keyframes are renamed with the same scope identity;
- unknown declarations and at-rules pass through;
- the compiler emits external CSS and source maps.

There is no client hydration or runtime style insertion. A component instance does not own a private shadow tree; normal inheritance and the cascade still work. The component contract will define ownership at child-component roots so a parent cannot accidentally style a child's entire internal tree.

Scope identity and asset identity solve different problems. Scope identity stays stable when source CSS changes, keeping initial HTML and streamed patches compatible. The emitted file uses a content hash, giving caches a new URL when bytes change.

## Tailwind is additive

Tailwind may generate utility classes from Woge Kotlin/HTML sources, but it does not replace the CSS contract. A semantic element can carry all three independent forms:

```html
<article class="project-card grid gap-4" data-woge-scope="w-42d82b1e3a9f">
  ...
</article>
```

`project-card` can come from application CSS, `grid gap-4` from Tailwind and the attribute from optional component scoping. Patching addresses Woge component identity, not CSS selectors or utility names. The [Tailwind integration contract](tailwind-integration.md) keeps extraction, versioning and production setup in an optional build adapter.

## Deployment rules

- Prefer external content-hashed CSS for caching and strict CSP.
- Deduplicate component assets in a deterministic page manifest.
- Establish layer and asset order at build/page assembly time.
- Do not add style tags from streamed patches.
- Apply production minification after any scope rewrite and retain source maps.
- Treat inline `<style>` and `style` attributes as explicit CSP choices, not defaults.

The implemented head helpers write ordinary `link`, `meta`, `style` and `script type="module"`
elements. Asset URLs pass through Woge's URL boundary; CSP nonces, Subresource Integrity metadata
and cross-origin mode are explicit values. See [Style pages and load assets](../guides/css-and-assets.md)
for executable usage guidance.

The executable [CSS authoring evidence](../../spikes/css-authoring/evidence.md) records the tested syntax,
browser versions and prototype limitations. [ADR 0016](../adr/0016-standards-native-css-authoring.md)
owns the direction; [ADR 0030](../adr/0030-materialize-css-and-head-asset-boundaries.md) records the
implemented API and security defaults.

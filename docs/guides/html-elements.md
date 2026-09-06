# Write HTML with the Woge DSL

Woge markup is Kotlin, but it deliberately reads like HTML. Standard element names are functions,
attributes stay visible, and nesting follows the document structure:

```kotlin
val markup = renderHtml {
    article(attributes = { classes("project-card", "grid", "gap-4") }) {
        header {
            h2 { text("Release Woge") }
            p { text("Ship a useful HTML-first slice.") }
        }
        a(attributes = { url("href", applicationUrl("/projects/woge")) }) {
            text("Open project")
        }
    }
}
```

You do not concatenate strings. `text(...)` escapes text, attributes escape quoted attribute values,
and `url(...)` accepts a validated `HtmlUrl`. `classes(...)` accepts ordinary CSS and Tailwind class
tokens without Woge interpreting them.

## Read the two lambdas

Most HTML elements accept an attribute block followed by a content block. Kotlin's trailing lambda
is therefore the nested HTML:

```kotlin
section(attributes = { attribute("id", "activity") }) {
    h2 { text("Activity") }
}
```

For a void element such as `img`, `input` or `br`, the only lambda is the attribute block:

```kotlin
img {
    url("src", applicationUrl("/assets/logo.svg"))
    attribute("alt", "Woge")
}
```

Trying to call `text(...)` inside `img { ... }` is a compiler error because an HTML void element
cannot have content. A misspelling such as `sectoin { ... }` is also a compiler error and IDE
completion suggests the standard `section` wrapper.

## Text-only and active elements

`title` and `textarea` contain text rather than nested HTML. Pass that text as the first argument;
Woge escapes it in the correct browser context:

```kotlin
title("Projects · Woge")
textarea("<strong>This stays text</strong>") {
    attribute("name", "description")
}
```

Active raw-text contexts are narrower. Inline CSS requires a `CssStylesheet`, while the general
`script` wrapper only exposes attributes and emits an empty body:

```kotlin
style(stylesheet("""
    .project-card { container-type: inline-size; }
""".trimIndent()))

script {
    attribute("type", "module")
    url("src", applicationUrl("/assets/application.js"))
}
```

Use `moduleScript(...)` when loading an application JavaScript module with CSP/SRI options. Woge does
not accept an ordinary `String` as inline JavaScript or inline style source by accident.

## Use new platform features immediately

Woge pins a reviewed standards dataset so its generated API and compiler diagnostics are
reproducible. You do not need to wait for a Woge release to use a custom element or a newly introduced
normal element:

```kotlin
element("project-timeline", attributes = { data("state", "ready") }) {
    text("Loaded")
}
```

`voidElement(...)` remains the matching low-level fallback when a future HTML standard introduces a
new void element. Custom elements themselves are normal elements and should use `element(...)` with
an end tag.

The generated wrappers intentionally do not model DOM interfaces, every possible attribute, CSS
properties, widgets or layout. Browser knowledge stays useful; Kotlin adds discoverability and safe
content boundaries around the HTML you already know.

The exact source version, license, derivation and update process live in the
[pinned dataset documentation](../../config/html-elements/README.md).

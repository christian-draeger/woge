# HTML writer strategy evidence

Recorded on 2026-09-03 with Kotlin 2.4.0, Gradle 8.14.4 and `kotlinx-html-jvm` 0.12.0, the latest Maven Central release observed during the spike.

## Compared fixture

Both renderers produce an `<article>` with:

- ordinary classes including `md:grid-cols-[minmax(0,1fr)_auto]`;
- a CSS declaration list containing `--accent: oklch(...)` and `container-type`;
- `data-state`, `aria-busy` and conditional `disabled`;
- hostile text containing `<script>`, quotes and `&`;
- a custom `<woge-status>` element.

The Woge prototype uses a generic `element("woge-status")`. `kotlinx.html` needs a small `HTMLTag` subclass and `FlowContent` extension for an ergonomic custom tag. Both allow arbitrary attributes through an attribute map/builder and leave CSS/Tailwind values as strings.

## Result matrix

| Concern | Woge-owned writer prototype | `kotlinx.html` 0.12.0 |
| --- | --- | --- |
| Text/attribute escaping | Explicit context functions; hostile fixture passes | Library escaping; hostile fixture passes |
| Boolean attributes | `boolean("disabled", condition)` emits presence/absence | Typed `disabled = condition`; presence/absence passes |
| `data-*` / `aria-*` / unknown attributes | Canonical `attribute` plus thin `data`/`aria` helpers | `attributes["..."]` |
| Custom elements/new tags | Generic `element(name)` works immediately | Generic `HTMLTag` helper requires consumer-aware boilerplate |
| Classes/Tailwind | Ordered string contributors; no utility parsing | Ordinary `classes` string |
| Inline CSS/custom properties | Ordered declaration-list strings | Ordinary `style` attribute string |
| Raw HTML | Only `raw(UnsafeHtml)` | Explicit `unsafe { raw(...) }` block |
| Incremental output | Every token writes to `HtmlSink`; first token observed before close | `appendHTML()` writes repeatedly to `Appendable`; no DOM |
| Woge sink interop | Native | A 16-line `Appendable` bridge writes into the same sink |
| IDE discovery | Small canonical primitives; common tag wrappers still required | Rich completion for known tags/typed attributes; wildcard/import surface is larger |
| New platform vocabulary | Generic fallback works without a Woge release | Attribute map works; ergonomic new/custom tags may await/helper code |
| Woge frame/region ownership | Direct sink and component boundary | Requires adapter types around `TagConsumer`/receivers |

Output formatting differs: `appendHTML()` may pretty-print whitespace, while the Woge writer emits only requested text. Tests compare semantic tokens where whitespace is not significant.

## Compiler and runtime failures

Ordinary Kotlin type checking rejects accidental raw HTML:

```text
RawString.kt:6:9 Argument type mismatch: actual type is 'String',
but 'UnsafeHtml' was expected.
```

It also rejects a string pretending to be Boolean presence:

```text
BooleanString.kt:7:29 Argument type mismatch: actual type is 'String',
but 'Boolean' was expected.
```

Invalid element/attribute names fail before any injected suffix is written. The prototype intentionally validates only syntax delimiters for generic attributes instead of maintaining an allowlist of known attributes. Text and quoted attributes use separate escaping paths.

## IDE and AI-DX assessment

A Woge-owned surface should expose one obvious `text`, `element`, `voidElement`, `attribute`, `boolean`, `classes`, `styles` and explicit raw-HTML path. `@DslMarker` prevents accidental calls through an outer receiver. Common standard tags can have generated/documented wrappers so completion resembles HTML; `element(name)` and `attribute(name, value)` remain the future/custom fallback.

The normal call site needs only Woge HTML/component imports. The optional adapter lets existing `kotlinx.html` extensions stream into Woge instead of converting through a DOM. Diagnostics stay ordinary Kotlin actual/expected errors, while generated component declaration errors follow the stable AI-DX diagnostic policy.

## Recommendation

Own the minimal `HtmlSink` and structural writer contract in Woge, because frame boundaries, trusted content, generated regions and adapter lifecycle are Woge responsibilities. Keep CSS, class utilities and unknown attributes as standards strings. Offer an optional streaming `kotlinx.html` adapter; do not fork or wrap every kotlinx tag type.

Do not ship the prototype unchanged. M1 must add common standard tag wrappers, URL-bearing attribute types, complete escaping/Unicode fixtures, void/raw-text element policy and sink failure semantics. The generic fallback prevents that convenience list from becoming an HTML feature gate.

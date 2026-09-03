# ADR 0012: Own a minimal streaming HTML writer with kotlinx.html interop

- Status: Accepted
- Date: 2026-09-03
- Decision owners: Woge maintainers
- Related issues: [#5](https://github.com/christian-draeger/woge/issues/5), [#6](https://github.com/christian-draeger/woge/issues/6), [#16](https://github.com/christian-draeger/woge/issues/16), [#17](https://github.com/christian-draeger/woge/issues/17), [#22](https://github.com/christian-draeger/woge/issues/22), [#42](https://github.com/christian-draeger/woge/issues/42), [#78](https://github.com/christian-draeger/woge/issues/78), [#88](https://github.com/christian-draeger/woge/issues/88)

## Context

Woge components need safe HTML output that can reach a host response incrementally and pause at generated region/frame boundaries. The API should look like HTML to a web developer, preserve current/future attributes and CSS, provide useful completion and make raw content visibly unsafe.

The [executable comparison](../../spikes/html-writer-strategy/evidence.md) rendered one utility-heavy custom-element component through a purpose-built writer and `kotlinx.html` 0.12.0. Both escaped hostile text, represented Boolean/custom/data/ARIA attributes and streamed to a sink without a DOM. `kotlinx.html` has mature known-tag completion; the purpose-built sink gives Woge direct control over region, framing and trusted-content boundaries. An `Appendable` bridge proved streaming interoperability.

## Decision

Woge owns a deliberately small `HtmlSink` and structural `HtmlWriter` contract. Generated/component rendering writes start tags, quoted attributes, escaped text, explicit raw content and end tags directly to the sink. The writer never builds or exposes an in-memory DOM. The sink decides buffering, flush, frame and downstream-failure behavior; the HTML DSL does not treat network chunks as frames.

The canonical concepts are:

- `text(value)` for escaped HTML text;
- common HTML tag/void-tag wrappers for completion and familiar nesting;
- `element(name)` and `voidElement(name)` as future/custom-element fallbacks;
- one generic quoted `attribute(name, value)` plus narrow convenience for Boolean presence, classes, declaration-list style, `data-*` and `aria-*`;
- an explicit `UnsafeHtml` (final name may change only before API freeze) required by `raw(...)`.

Text and quoted-attribute contexts have separate encoders. Element/attribute syntax rejects delimiter/control injection but generic APIs do not use a catalog of known platform names. URL-bearing attributes use typed/validated URL values where Woge creates them; application-owned custom attributes remain strings. Script/raw-text/style handling receives explicit APIs and security/CSS review instead of passing through the normal text encoder accidentally.

Common tag wrappers follow browser names and documentation rather than inventing a Compose-like widget vocabulary. The wrapper set is generated or mechanically maintained from standards data, but missing/new/custom elements remain usable immediately through `element`. Attribute values, class lists, Tailwind utilities, inline declaration lists and CSS custom properties are ordinary ordered strings. Woge does not generate typed CSS properties or utility classes.

A DSL marker prevents accidental outer-receiver calls. Canonical methods avoid several equivalent spellings. Public examples use explicit `text(...)` rather than unary operators so a web developer new to Kotlin can identify escaping behavior at a glance.

An optional `woge-html-kotlinx` interoperability artifact adapts a Woge sink to `Appendable`/`TagConsumer` so existing `kotlinx.html` blocks/extensions can stream inside an explicitly bounded component render. `kotlinx.html` types do not enter Woge core, protocol, host SPI or generated descriptor signatures. Rendering from other template engines crosses a documented buffered/trusted boundary; Woge never guesses that an arbitrary string is safe HTML.

The spike prototype is evidence, not production code. M1 implementation must settle sink failure/cancellation, complete escaping and Unicode behavior, tag wrapper generation, raw-text/void rules, URL attributes and source diagnostics before publishing the API.

## Alternatives considered

- **Expose `kotlinx.html` as the only public rendering API:** rejected as the core contract because Woge-specific frame/region/trusted boundaries would depend on external consumer/receiver types and custom/new tags need additional helpers. It remains a supported adapter.
- **Build a complete typed replacement for every tag, attribute and value:** rejected because it would lag the platform and create a second HTML vocabulary.
- **Use only `element(name)` and maps:** rejected as the documented path because common tags would lose IDE completion, hover documentation and typo detection.
- **Render a complete string/DOM before writing:** rejected because it prevents shell-first and completion-order output and raises memory use.
- **Accept raw HTML as `String`:** rejected because safe and unsafe content become indistinguishable at review and compile time.
- **Use unary plus as the only text API:** rejected as the canonical teaching form because its escaping behavior is not obvious to web developers unfamiliar with Kotlin DSL conventions.
- **Make Tailwind/CSS properties typed Kotlin enums:** rejected because ordinary class/CSS strings already preserve the ecosystem and new web standards.

## Consequences

### Positive

- Woge owns the exact streaming, trusted-content and generated-region boundary it must keep stable.
- Common rendering reads like HTML while generic fallbacks keep new platform vocabulary open.
- Raw content and Boolean presence errors receive normal compiler feedback.
- Existing `kotlinx.html` code can stream through an optional adapter without infecting portable descriptors.
- Plain CSS, modern declaration strings and Tailwind classes compose without framework parsing.

### Negative

- Woge must maintain common tag wrappers, escaping tests and HTML syntax behavior.
- The generic fallback cannot offer the same completion as known wrappers.
- `kotlinx.html` interoperability adds a separate artifact and receiver boundary.
- Template-engine output may require buffering or an explicit trusted conversion.

## Follow-up

- Implement production escaped text/attributes, Boolean/custom attributes and raw-content boundaries in [#16](https://github.com/christian-draeger/woge/issues/16).
- Implement buffered/streaming sinks and downstream failure behavior in [#17](https://github.com/christian-draeger/woge/issues/17).
- Add common tag wrappers, generic future-tag fixtures and compile-verified newcomer examples in [#73](https://github.com/christian-draeger/woge/issues/73) and [#74](https://github.com/christian-draeger/woge/issues/74).
- Prototype the optional `woge-html-kotlinx` artifact only after the core sink works; keep it out of the initial mandatory graph until demanded by a consumer fixture.
- Decide CSS language injection, style-block/declaration types and optional scoping in [#88](https://github.com/christian-draeger/woge/issues/88).
- Keep HTML framing separate and choose it in [#6](https://github.com/christian-draeger/woge/issues/6).

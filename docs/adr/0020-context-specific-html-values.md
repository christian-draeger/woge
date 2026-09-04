# ADR 0020: Separate HTML value contexts and make active contexts explicit

- Status: Accepted
- Date: 2026-09-04
- Decision owners: Woge maintainers
- Related issues: [#16](https://github.com/christian-draeger/woge/issues/16), [#42](https://github.com/christian-draeger/woge/issues/42), [#88](https://github.com/christian-draeger/woge/issues/88)

## Context

[ADR 0012](0012-html-writer-and-kotlinx-interop.md) chose a small streaming writer and an explicit
raw-content type, but left the exact value boundaries to M1. HTML text, quoted attributes, URLs,
`srcdoc`, CSS and raw-text elements do not share one browser parsing context. Treating them all as a
string operation would make a safe-looking call change meaning when moved to another context.

Woge must also remain open to custom elements and attributes and to HTML features added after a Woge
release. A closed enum of all platform vocabulary would improve completion temporarily but would
prevent web developers from using the evolving platform.

## Decision

`woge-core` provides distinct operations for HTML text, quoted attribute data, Boolean presence,
validated URL values and deliberately unescaped content:

- `text(value)` encodes data for HTML text parsing;
- `attribute(name, value)` always emits a quoted, attribute-encoded value;
- `boolean(name, present)` emits presence or absence and never serializes `true` or `false`;
- `data`, `aria`, `classes` and `styles` preserve ordinary web strings and deterministic source order;
- `url(name, value)` accepts only an `HtmlUrl` created by a validating factory;
- `raw(UnsafeHtml)`, `srcdoc(UnsafeHtml)`, `unsafeUrl` and `unsafeAttribute` are explicit escape hatches.

Unsafe value factories and consumers require Kotlin opt-in at error level. The type records that the
application, not Woge, audited or sanitized the value for the exact browser context; it is not a
sanitizer and carries no automatic trust between contexts.

Text and attribute encoders are separate even where their current escape sets overlap. Both replace
NUL and unpaired UTF-16 surrogates with U+FFFD. Text encodes `&`, `<` and `>`; quoted attributes also
encode both quote characters and carriage return. Attribute names reject HTML syntax delimiters,
control characters, whitespace and unpaired surrogates before any start tag is written. Duplicate
names are rejected case-insensitively.

Known URL-bearing, Boolean, `srcdoc` and inline-event names cannot pass through the ordinary attribute
method. This is a guard for current platform vocabulary, not a permanent allowlist. A future or custom
URL-bearing attribute can use `url(name, HtmlUrl)` immediately, while an ordinary future/custom
attribute can use `attribute`. Inline event handlers remain unsafe even if their name is new.

`applicationUrl` accepts a non-empty relative reference with no scheme, authority, control,
whitespace, backslash, bidirectional control or malformed URI syntax. `externalUrl` accepts absolute
`http`, `https`, `mailto` and `tel` references; HTTP(S) requires an authority and rejects embedded
user-info. Callers percent-encode dynamic path and query components before constructing either value.
Multi-URL syntax and intentionally active schemes require `UnsafeHtmlUrl`; ordinary control and
Unicode checks still apply so source review remains reliable.

Class names, Tailwind utilities, CSS declarations and custom properties remain ordered strings. HTML
attribute encoding prevents a declaration string from breaking out of `style="..."`; it does not make
untrusted CSS safe or prevent CSS from loading resources. Applications compose only application-owned
declarations until a separately reviewed CSS value boundary exists.

The normal element writer rejects void elements and HTML raw-text elements used through the wrong
operation. Dedicated style, script and other raw-text APIs are deferred rather than pretending that
HTML text encoding is valid in those parser states.

## Alternatives considered

- **One HTML-escape function for every value:** rejected because HTML text, attribute, URL, CSS and
  raw-text parser states have different rules.
- **Sanitize arbitrary HTML inside `raw`:** rejected because sanitization policy depends on the
  application and must not be confused with output encoding.
- **Accept URL attributes as strings and strip suspicious prefixes:** rejected because browser URL
  parsing is subtle and a distinct Kotlin type makes the security decision visible at the call site.
- **Allow only a fixed catalog of attributes:** rejected because it would make Woge lag custom
  elements and new web standards.
- **Parse class lists or CSS into a Woge-owned model:** rejected because it would constrain Tailwind,
  custom properties and current CSS without improving HTML-context safety.

## Consequences

### Positive

- Ordinary model text cannot become markup by changing its contents.
- Kotlin rejects a plain string at raw-HTML and validated-URL boundaries before an application runs.
- Reviewers and coding models can distinguish safe defaults from deliberate escape hatches by type
  and opt-in annotation.
- Custom elements, Tailwind classes, new CSS and future attributes remain usable without waiting for a
  Woge release.
- Attribute order and class/style composition are deterministic for tests and generated output.

### Negative

- Woge maintains escaping, current active-attribute knowledge and URL-policy tests.
- A generic future attribute cannot be classified before browsers define its semantics; developers
  must select `url` or an unsafe boundary when that new attribute enters an active context.
- The strict URL factories require callers to compose and percent-encode dynamic components first.
- `UnsafeHtml` and `UnsafeHtmlUrl` are audit markers, not proof that content is safe.
- Safe CSS value composition and raw-text element authoring need later context-specific APIs.

## Follow-up

- Add common tag/attribute wrappers and generated diagnostics in [#73](https://github.com/christian-draeger/woge/issues/73).
- Add the CSS language-injection and style-block boundary in [#88](https://github.com/christian-draeger/woge/issues/88).
- Run the broader browser XSS corpus and patch-insertion checks in [#42](https://github.com/christian-draeger/woge/issues/42).
- Keep the compiled examples aligned with the public DSL while implementing [#17](https://github.com/christian-draeger/woge/issues/17) and [#24](https://github.com/christian-draeger/woge/issues/24).

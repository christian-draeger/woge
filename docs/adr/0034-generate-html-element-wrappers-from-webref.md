# ADR 0034: Generate HTML element wrappers from pinned Webref data

- Status: Accepted
- Date: 2026-09-06
- Decision owners: Woge maintainers
- Related issues: [#131](https://github.com/christian-draeger/woge/issues/131), [#73](https://github.com/christian-draeger/woge/issues/73)

## Context

The streaming writer established by ADR 0012 safely serializes arbitrary normal elements, but a small
hand-written wrapper set made ordinary HTML inconsistent. Common tags had completion and typo
checking while equally standard tags required strings. Maintaining a full catalog manually would
drift from the platform and produce review noise.

Generated wrappers must not turn Woge into a Kotlin DOM, freeze the open-ended HTML attribute model,
or introduce a typed CSS/widget hierarchy. They must preserve HTML's syntax distinctions. In
particular, void elements cannot receive children, `title` and `textarea` contain escapable text, and
raw-text elements must not silently accept active source strings.

Standards data also affects the public API. Builds need a reproducible, reviewable input instead of a
network lookup whose result changes over time.

## Decision

Woge derives its common HTML wrapper surface from the conforming entries in the W3C Webref
`@webref/elements` package. Version 2.8.0 is reduced to a checked-in 113-row TSV containing the element
name, serialization kind, DOM interface and WHATWG specification URL. Package version, upstream
commit, integrity hashes, derived-data checksum and MIT license are recorded beside that file.
Obsolete entries are excluded.

A cacheable Gradle task generates Kotlin source under `build/generated` during `woge-core`
compilation. The generated KDoc links each wrapper to its specification and names the corresponding
DOM interface for search and AI context; Woge does not expose that interface as a runtime type.
Kotlin keywords such as `object` and `var` use backticked HTML names.

Normal elements accept `Attributes.() -> Unit` and nested `HtmlWriter.() -> Unit`. Void elements only
accept attributes. `title` and `textarea` accept an escaped `String` plus attributes. `script` and
`iframe` expose attributes with an empty raw-text body. `style` keeps its specialized
`CssStylesheet` API. Ordinary strings therefore cannot cross an active content boundary, and the
compiler rejects nested markup in void or text-only elements.

The low-level `element(...)` and `voidElement(...)` APIs remain public. They are the immediate route
for custom elements and newly standardized syntax; known context-specific names still cannot bypass
their safe writer path. Attributes remain open strings with focused typed boundaries for URLs, CSS,
unsafe markup and related active contexts.

The normal `check` lifecycle verifies the pinned TSV checksum and deterministic generation. ABI
validation records every generated public wrapper. Updating the standards pin therefore requires an
explicit provenance, classification and API review.

## Alternatives considered

- **Continue with hand-written common wrappers:** rejected because coverage and KDoc would drift and
  every standards update would be repetitive implementation work.
- **Download the latest Webref data during each build:** rejected because builds and public APIs would
  depend on network state and an unreviewed moving input.
- **Generate every attribute, DOM interface and content model:** rejected because the platform is
  open-ended, many content models cannot be usefully expressed in Kotlin overloads, and web
  developers would have to learn a parallel DOM type system.
- **Adopt `kotlinx.html` as the public DSL:** rejected by ADR 0012 because Woge needs a small owned
  streaming boundary. Interoperability can remain additive.
- **Allow arbitrary inline script and style strings:** rejected because those are active browser
  contexts and need explicit, auditable value types.

## Consequences

### Positive

- IDE and AI completion cover all 113 conforming core HTML elements from the pinned source.
- Kotlin signatures catch tag typos, void-element children and active-context misuse at compile time.
- KDoc keeps familiar HTML names and links directly to the living platform specification.
- New and custom elements remain available without waiting for generator updates.
- Clean and incremental builds are deterministic and do not use the network.

### Negative

- A standards update is an intentional source-data and public-ABI change, not an automatic refresh.
- The generated functions live in a different JVM file class from the earlier pre-1.0 hand-written
  subset; this is a binary change before Woge's compatibility freeze.
- HTML content models beyond the safety-critical syntax categories are documented rather than
  encoded as an exhaustive Kotlin type graph.
- Inline script remains unavailable until Woge defines a separate audited source boundary.

## Follow-up

- Keep the dataset pin and ABI review in dependency-update maintenance.
- Use the full wrapper surface in reference examples as features need it; do not add a second manual
  tag catalog.
- Evaluate generated route and action descriptors separately; they must not be coupled to this HTML
  standards generator.

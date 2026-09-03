# Tailwind integration

Tailwind is a supported optional build workflow, not Woge's styling model. Web developers use its documented class names and CSS configuration directly; Woge does not wrap utilities in Kotlin property functions.

This page records the M0 integration contract. The Gradle plugin/API is not released yet.

## Use complete class names

Tailwind scans source text. It does not execute Kotlin, so every possible utility name must appear as a complete token:

```kotlin
val toneClasses = when (tone) {
    Tone.INFO -> "bg-brand-500 text-white"
    Tone.WARNING -> "bg-amber-100 text-amber-950"
}
```

Avoid this:

```kotlin
val toneClass = "bg-${tone}-500"
```

The second form can render a class that Tailwind never generated. Use a map/`when` with complete values, or add a deliberate `@source inline(...)` entry when a class does not exist literally in scanned source. The planned Gradle adapter may flag common interpolated/concatenated forms, but its diagnostic is a guardrail rather than a Kotlin evaluator.

## Make sources explicit

Woge's reference configuration disables Tailwind's automatic source discovery and registers:

- application Kotlin template roots;
- Woge generated-descriptor output roots;
- each source-distributed component root or its future candidate manifest;
- explicit inline candidates used as a reviewed safelist.

Source generation must finish before Tailwind extraction. Paths are part of the Gradle task inputs, so changes invalidate the CSS task predictably. Binary component distribution and transitive candidate discovery are decided with the component packaging model in issue #76.

## Keep plain CSS separate

Application CSS from the [standards-native contract](css-authoring.md) stays in its own external asset and is copied byte for byte. Tailwind processes a separate CSS-first input containing `@import "tailwindcss"`, `@source` and `@theme`.

```html
<link rel="stylesheet" href="/assets/application.css">
<link rel="stylesheet" href="/assets/tailwind.min.css">
```

This prevents Tailwind extraction/minification from silently translating application-owned nesting, selectors or future syntax. Both assets participate in the normal cascade. Their order and layer names are explicit application decisions.

## Gradle boundary

The future adapter is build tooling outside `woge-core` and all server/browser runtimes. It owns:

- dependencies between descriptor generation, candidate validation, Tailwind execution and resource assembly;
- declared inputs/outputs for incremental and reproducible builds;
- normalization/externalization of source maps;
- exact asset synchronization so deleted/renamed outputs do not remain in a JAR;
- actionable diagnostics when the selected CLI is unavailable;
- development watch tasks.

It does not own semantic HTML, component identity, patch selection, runtime class mutation or browser HMR. A Spring/Vite/proxy development environment may reload the generated asset, while production serves static content-hashed files normally.

## Toolchain modes

The locked npm CLI is the reference mode. An official standalone executable is a valid optional mode for a Kotlin/Spring project that otherwise needs no Node.js. Standalone downloads must be versioned per platform and verified against a committed SHA-256 digest; an ordinary build never follows `latest`.

Woge releases name one exact tested Tailwind version. Overrides outside that tested version are possible build-tool choices but are not covered by Woge compatibility claims.

The executable [Tailwind/Kotlin evidence](../../spikes/tailwind-kotlin/evidence.md) records extraction, watch, output, source-map and standalone parity. [ADR 0017](../adr/0017-optional-tailwind-build-adapter.md) owns the decision.

# CSS authoring spike

This spike asks a deliberately simple question: can Woge keep CSS feeling like CSS while still giving Kotlin authors useful types and editor help?

The answer is yes. The prototype keeps three ordinary web authoring paths:

1. Link an external `.css` file. This is the default and needs no Woge processing.
2. Pass a complete stylesheet to `stylesheet("...")` for a page or component style block.
3. Pass declarations to `declarations("...")` for an HTML `style` attribute.

The two Kotlin functions accept normal strings. They do not enumerate CSS properties or parse away unknown syntax. Their distinct result types stop a complete stylesheet from being rendered as an attribute by mistake. JetBrains' `@Language` annotation tells IntelliJ IDEA that the source string contains CSS; the declaration function supplies a synthetic selector through the annotation's `prefix` and `suffix` so the IDE parses the source as declarations.

```kotlin
val accent = "oklch(68% 0.18 35)"

val cardCss = stylesheet(
    """
        @layer components {
          .card {
            --accent: $accent;
            container-type: inline-size;
            color: var(--accent);

            & > h2 { margin-inline: 1rem; }
          }
        }
    """.trimIndent(),
)

val cardStyle = declarations("view-transition-name: project-card;")
```

These are spike APIs, not yet a released Woge package. See the [evidence and recommendation](evidence.md) and [CSS authoring contract](../../docs/architecture/css-authoring.md) before reusing them.

## Run the evidence

The browser suite needs the Playwright browsers installed once:

```shell
cd spikes/css-authoring
npm ci
npx playwright install chromium firefox webkit
./validate.sh
```

The validator runs Kotlin unit tests, two expected compiler failures, a bytecode check for IDE metadata, selector/keyframe/source-map tests and the CSS fixture in Chromium, Firefox and WebKit.

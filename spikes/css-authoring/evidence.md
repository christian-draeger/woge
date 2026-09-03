# Standards-native CSS authoring evidence

Recorded on 2026-09-04 with Kotlin 2.4.0, JetBrains Annotations 26.1.0, Node.js 26.3.0 and Playwright 1.62.1. The Playwright registry pins Chrome for Testing 151.0.7922.34, Firefox 153.0 and WebKit 26.5. The local IDE used for the project smoke check was IntelliJ IDEA 2026.1.4 (IU-261.26222.65).

## Result

The recommended contract is a hybrid of ordinary external CSS, unscoped CSS strings with IDE metadata, and optional build-time component scoping. Woge should not build a comprehensive typed CSS-property DSL.

External CSS is the zero-magic baseline. The Kotlin prototype adds only context types and output-context escaping; it deliberately preserves CSS text it does not understand. The scoped prototype is viable as optional build tooling if its full selector corpus and production asset pipeline are completed later. Tailwind remains an independent class-generation/tooling adapter.

## Executable matrix

| Concern | Evidence | Result |
| --- | --- | --- |
| Modern CSS | External fixture uses cascade layers, native nesting, a container query, custom properties, logical properties, `oklch()`, `@supports`, reduced-motion media and a view-transition name | Computed-style assertions pass in all three browser engines |
| Page and declaration CSS | The fixture combines a linked stylesheet, a page `<style>` and a `style` attribute | All sources participate in the ordinary cascade |
| Unknown CSS | Kotlin tests pass an invented future property through unchanged | No Woge property allowlist or translation |
| Kotlin interpolation | Tests cover an interpolated color and Kotlin's literal-dollar spelling | CSS bytes remain ordinary Kotlin string bytes |
| Output contexts | `CssStylesheet` and `CssDeclarations` are distinct inline value classes | Two negative fixtures fail compilation when contexts are crossed |
| Raw-text and attribute escaping | Style blocks reject a case-insensitive `</style` sequence; declarations escape the HTML attribute context | Tested on the JVM |
| IDE metadata | Complete sheets use `@Language("CSS")`; declaration lists add a synthetic rule with `prefix` and `suffix` | Annotation is retained in compiled bytecode; no runtime IntelliJ dependency |
| Scoped selectors | PostCSS prototype adds `:where([data-woge-scope=...])` to the rightmost local subject and unwraps explicit `:global(...)` compounds | Selector fixtures pass without adding specificity |
| Scoped keyframes | Local keyframe names and animation references are rewritten; `:global(name)` remains public | Fixture passes |
| Stable identity | Scope value is derived from a qualified component identity, not CSS content | Same component stays stable across CSS edits and server patches |
| Source maps | The prototype emits an external source map with original source content | Mapping is non-empty and source identity is asserted |
| Tailwind coexistence | Semantic `<article>` carries ordinary utility/component classes and a separate scope attribute | DOM and style assertions pass without patch/runtime behavior |

One validator run produces five Kotlin unit passes, two expected compiler diagnostics, four Node scoping passes and six browser passes (two tests in each of Chromium, Firefox and WebKit).

`npm audit` reported zero known vulnerabilities for the locked spike dependency tree on the recording date.

## IntelliJ contract

JetBrains documents `@Language` as the supported way to mark parameters and other code elements as language injections. Injected CSS receives the CSS editor's coding assistance; JetBrains explicitly describes highlighting, completion and validation for injected languages. The annotation's `prefix` and `suffix` make a fragment parseable in a synthetic context, which lets `color: ...; --token: ...` remain a declaration list instead of pretending to be a stylesheet.

The dependency is `compileOnly`. `javap` confirms that the annotation and its arguments survive in the compiled method metadata, while the Woge runtime does not load or call IntelliJ code. Other editors see valid Kotlin strings and can still edit linked `.css` files normally. A visual completion smoke belongs in release/manual IDE checks because IDE UI behavior is not a stable headless CI interface.

Interpolation has a real tooling boundary: IntelliJ can inspect the static CSS around a Kotlin template expression, but it cannot prove that an arbitrary runtime string is a valid CSS token or safe value. Documentation and generated examples should prefer fixed CSS, custom properties with constrained values, classes and data attributes. Untrusted input must never be interpolated into CSS source.

## Scoping prototype

The prototype hashes a stable qualified component identity into a `data-woge-scope` value. Rendered elements receive that value during normal server rendering, so initial HTML and later patches use the same identity without hydration. Changing the CSS does not change markup identity; production CSS files can still use a separate content hash for caching.

The selector rewriter:

- appends a zero-specificity `:where([data-woge-scope=...])` guard to the rightmost local selector subject;
- leaves explicitly wrapped `:global(...)` compounds unscoped;
- scopes local keyframe names and matching `animation`/`animation-name` words;
- preserves modern at-rules and declarations through PostCSS;
- emits a source map back to the component CSS source.

This is evidence, not a production compiler. Before shipping, the scoper needs a larger conformance corpus for nested selectors, functional pseudos, selector lists, escaped identifiers, multiple animations, custom properties and malformed input. Component ownership rules must also define exactly which root/descendant elements receive a parent's scope attribute.

## Production consequences

- **CSP:** external same-origin CSS works with a strict `style-src`. Inline `<style>` needs a nonce or hash. HTML `style` attributes may require a weaker `style-src-attr` policy, so Woge examples should not depend on them for a strict-CSP application.
- **Caching:** compiled CSS is emitted as an external content-hashed asset. The stable scope identity and the cache identity are intentionally separate.
- **Deduplication:** a page asset manifest includes a component stylesheet once, even if the component renders many times or in later patches.
- **Ordering:** the build emits deterministic layer and asset order. A patch references already declared assets; it does not inject a `<style>` tag at patch time.
- **Minification:** production minification runs after selector/keyframe rewriting and must preserve custom properties, unknown syntax and source maps. Woge does not minify at runtime.
- **Browser policy:** syntax support follows the per-feature compatibility policy. A parser accepting draft syntax does not turn it into a Woge guarantee.

## Options compared

| Option | Strength | Cost / decision |
| --- | --- | --- |
| External or application-owned CSS | Best browser/tool compatibility, CSP and cache behavior | **Accepted as the default** |
| Unscoped stylesheet/declaration strings | Convenient near a component; useful Kotlin context types and IntelliJ injection | **Accepted as an authoring option** |
| Build-time component scoping | Local ownership without runtime injection; source maps are possible | **Accepted as an optional future tool**, subject to compiler conformance work |
| Comprehensive typed Kotlin CSS DSL | Can constrain known properties | **Rejected**: duplicates an evolving platform, delays new CSS, weakens transferable web knowledge and still needs raw escape hatches |

## Reproduction

```shell
cd spikes/css-authoring
npm ci
npx playwright install chromium firefox webkit
./validate.sh
```

## Primary references

- [W3C CSS Snapshot 2026](https://www.w3.org/TR/css-2026/)
- [IntelliJ IDEA: language and reference injections](https://www.jetbrains.com/help/idea/using-language-injections.html)
- [IntelliJ IDEA: annotating language injections](https://www.jetbrains.com/help/idea/annotating-source-code.html#annotating-language-injections)
- [JetBrains JVM annotations](https://github.com/JetBrains/java-annotations)
- [Svelte scoped styles](https://svelte.dev/docs/svelte/scoped-styles)
- [PostCSS](https://postcss.org/)
- [Playwright browser coverage](https://playwright.dev/docs/browsers)

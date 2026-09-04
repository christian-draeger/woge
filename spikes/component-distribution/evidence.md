# Component distribution evidence

Recorded on 2026-09-04 with Kotlin 2.4.0, Gradle 8.14.4 and Node.js 26.3.0.

## Result

Woge should combine stable binary headless primitives with optional source-owned visual recipes. This keeps semantics, escaping, form behavior and patch identity centrally fixable while giving applications direct ownership of Kotlin composition and ordinary CSS or Tailwind classes.

A MUI-style styled binary library remains possible for deliberately narrow starter themes, but it is not the primary component contract. A source-only catalog is flexible but makes every application responsible for merging behavior, accessibility and security fixes. Headless-only distribution is safe and small but does not meet the requested batteries-included developer experience by itself.

## One screen, four models

All prototypes render the project board from the same typed model. The screen has a URL-backed filter form, labelled controls, a data table with owner and progress columns, links, CSRF-bearing mutation forms, an `aria-live` result, and an independently replaceable rows region.

| Model | Real customization | What worked | Material cost |
| --- | --- | --- | --- |
| Binary headless primitive | Typed owner/progress/density options; application wrapper can add its own content and CSS | Central fixes, compiler guidance, stable instance IDs and patch region, no browser asset | Styling and richer composition still need a recipe |
| Source-owned registry component | Installed source was changed to an Aurora portfolio summary, average progress, owner/progress table and app CSS | Maximum local control; plain CSS and Tailwind render the same semantics; normal code review | The application owns copied escaping, forms and accessibility and must merge upstream fixes |
| Styled binary component | High-contrast theme and compact density | Small setup; theme update reaches all consumers | Only predefined variants are easy; DOM/CSS become compatibility surface and deep customization pushes toward slots or forks |
| Hybrid | A 25-line source recipe composes the binary semantic board with application-owned modern CSS | Central behavioral fixes plus direct source/CSS ownership; smallest customized recipe | Requires two coordinated release surfaces and a registry/update tool |

The source-owned and hybrid versions are not cosmetic hello-world cards: they preserve the complete filter/table/mutation/patch journey and are customized beyond the default component.

## Executable checks

| Concern | Evidence |
| --- | --- |
| SSR and progressive enhancement | Kotlin tests require useful links, `GET` and `POST` forms, table semantics and status feedback and reject script/hydration markers |
| Streaming patches | Full renders contain the same stable instance-qualified rows fragment returned by patch rendering |
| Identity | Two board instances produce different heading/input IDs and region names; scope is a deterministic 12-hex SHA-256 prefix |
| Kotlin guidance | Status and density are enums; IDs and progress validate at construction; a negative fixture proves a string density fails compilation with the expected type diagnostic |
| Output safety | Hostile heading, query and CSRF fixtures are escaped in their HTML contexts |
| Plain CSS/Tailwind parity | A test removes only `class`, style-mode and stylesheet attributes and then requires byte-identical semantic HTML |
| Registry integrity | Both immutable manifests verify every source SHA-256, expose license/provenance/compatibility/hydration/Tailwind metadata, and reject target path traversal |
| Update safety | Reinstall refuses a locally changed file; the v0.1-to-v0.2 plan distinguishes `replace-safe` from `merge-required` and leaves source untouched |
| Packaged-library contents | CI inspects the binary JAR for its CSS and component manifest |
| AI editing | Machine-readable manifests, complete static Tailwind tokens, enums, explicit defaults, deterministic files and source-located compiler errors avoid reflection or convention guessing |

One validation run executes six Kotlin tests, five registry tests, two manifest verifications, one expected Kotlin compiler failure and two JAR-content checks.

## Measured artifact/source size

These are spike-scale measurements, not release budgets. JAR sizes are compressed server artifacts; CSS is the browser-delivered part. Every model adds zero JavaScript bytes.

| Artifact/source | Bytes | gzip bytes | Lines |
| --- | ---: | ---: | ---: |
| Headless binary JAR | 17,103 | 15,800 | — |
| Styled binary JAR | 4,551 | 3,566 | — |
| Styled binary CSS | 601 | 274 | 23 |
| Registry Kotlin source | 3,320 | 1,040 | 76 |
| Registry plain CSS | 412 | 265 | 19 |
| Customized Kotlin source | 3,774 | 1,170 | 86 |
| Customized plain CSS | 540 | 273 | 25 |
| Hybrid Kotlin recipe | 1,000 | 364 | 25 |
| Hybrid plain CSS | 427 | 242 | 16 |

The hybrid recipe is about one third of the source-owned renderer because the form, table, escaping and patch behavior remain in the headless binary. Production thresholds still belong to the project performance budget work.

## CSS and Tailwind ownership

Registry recipes expose Kotlin candidate files explicitly. Utility strings are complete static tokens, so the Tailwind adapter from ADR 0017 can scan them without evaluating Kotlin. Plain CSS remains a normal source file using cascade layers, container queries, logical sizing and modern color functions. An application may move a recipe between the two style modes without changing links, forms, region identity or server behavior.

Packaged binary CSS is less flexible: selectors, layer names, custom properties and theme attributes become a second compatibility surface. That is acceptable for an optional starter theme but not as the only route to a comprehensive Woge component catalog.

## Reproduction

```shell
./spikes/component-distribution/validate.sh
```

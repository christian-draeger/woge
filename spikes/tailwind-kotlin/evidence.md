# Tailwind Kotlin integration evidence

Recorded on 2026-09-04 with Tailwind CSS and `@tailwindcss/cli` 4.3.3, Kotlin 2.4.0, Gradle 8.14.4, Node.js 26.3.0 and the official Tailwind 4.3.3 macOS arm64 standalone executable.

## Result

Tailwind is viable as a first-class optional Woge build adapter. It does not belong in `woge-core`, the semantic component API or the patch runtime.

The reference path uses Tailwind v4's CSS-first CLI through Gradle and a locked npm dependency tree. A checksummed standalone executable can implement the same executor boundary for projects that otherwise need no Node.js. Both tested paths emitted identical utility CSS.

## Source discovery

The input starts with automatic discovery disabled and names every supported source explicitly:

```css
@import "tailwindcss" source(none);
@source "../kotlin";
@source "../../../build/generated/sources/woge";
@source "../../../fixtures/source-distributed";
@source inline("motion-safe:animate-spin");
```

This makes monorepo/build-directory behavior reviewable and avoids relying on a caller's current working directory or `.gitignore` heuristics. `generateWogeDescriptors` runs before Tailwind and emits complete static tokens. A source-distributed component fixture is also scanned explicitly; binary/component packaging still needs the source-root or candidate-manifest contract owned by issue #76.

| Candidate source | Representative evidence | Result |
| --- | --- | --- |
| Hand-written Kotlin | `grid`, `hover:shadow-lg`, `md:grid-cols-2`, an arbitrary grid value and `aria-busy:opacity-60` | Selectors generated |
| Runtime choice | `INFO` and `WARNING` map to two complete static class groups | Both states generated; rendered state contains one group |
| Generated descriptor | `animate-pulse`, `ring-2`, `ring-brand-500` under `build/generated` | Generated before extraction and compiled as Kotlin |
| Source-distributed Kotlin | border, logical-border, theme color and `supports-[display:grid]` classes | Selectors generated from the explicit external source root |
| Explicit safelist | `motion-safe:animate-spin` through `@source inline(...)` | Selector and media condition generated |
| Dynamic negative fixture | `"bg-${tone}-500"` | Source-located policy diagnostic; no accidental `.bg-red-500` output |

Tailwind's scanner treats source as plain text; it cannot prove arbitrary Kotlin evaluation. The adapter's conservative check catches common interpolated/concatenated utility prefixes, but the durable rule is simpler: map runtime state to complete static tokens or declare an explicit inline source. Woge does not introduce a Tailwind utility DSL.

## Plain CSS coexistence

The 393-byte `application.css` fixture uses a cascade layer, native nesting, `oklch()`, a custom property, container queries and logical properties. Gradle copies it byte for byte. Tailwind receives a separate `tailwind.css` input and cannot rewrite or discard the application stylesheet.

The Kotlin renderer links both assets and adds ordinary class strings. A test removes only stylesheet links and class attributes from the styled output and obtains byte-identical semantic HTML to the plain renderer. Woge patch identity is not a CSS selector or utility class.

## Gradle and development loop

The prototype declares Kotlin sources, generated sources, the Tailwind input, lockfile and wrapper script as Gradle inputs. It declares CSS/map files as outputs, runs source generation before extraction, and synchronizes exactly three current assets into the resource tree:

- `application.css`
- `tailwind.min.css`
- `tailwind.min.css.map`

The synchronizing task removes stale generated assets and the JAR fixture contains only those three files. A `tailwindCssWatch` task uses Tailwind 4.3.3's polling mode and does not require the optional Parcel install script. The executable test changed a generated Kotlin source and observed the rebuilt `underline` utility in about 0.22 seconds end to end.

The task provides watched CSS rebuilds, not a Woge-specific browser HMR protocol. Spring Boot DevTools, a local proxy or another application dev server may reload the linked stylesheet/page. That boundary keeps development tooling out of the production runtime.

## Production output and source maps

Five child-process builds on the local arm64 machine measured:

| Measurement | Result |
| --- | ---: |
| End-to-end build time | 77.5 ms minimum, 78.5 ms median, 82.0 ms maximum |
| `tailwind.min.css` | 8,224 bytes |
| gzip (deterministic in-memory compression) | 2,510 bytes |
| External source map | 31,859 bytes |
| Five-build output hashes | One unique SHA-256 |

Tailwind's CLI emits an inline source map containing checkout-specific absolute paths. The adapter decodes it, normalizes sources to `src/main/css/tailwind.css` and `tailwindcss/index.css`, writes an external map and leaves a relative map reference in CSS. Rebuilding to the same artifact path is byte-for-byte deterministic for both CSS and map.

Diagnostics are intentionally early: missing npm installation fails before the CLI task, dynamic candidates report file and line, Kotlin examples compile, and Gradle task validation checks undeclared dependencies/outputs.

## npm CLI versus standalone

| Property | Locked npm CLI | Official standalone |
| --- | --- | --- |
| Installed local size in this fixture | 18,340 KiB `node_modules` | 79,826,018-byte macOS arm64 executable |
| Dependency control | `package-lock.json`, npm audit/renovation ecosystem | Exact release URL plus published SHA-256 |
| Node.js required | Yes | No |
| Watch | CLI watch; polling works without optional install scripts | Same CLI contract |
| Generated minified CSS | 8,180 bytes excluding map comment | Byte-identical 8,180 bytes |

The npm CLI is the reference mode because it is smaller here, integrates naturally with web tooling and has standard lockfile/update automation. Standalone is a supported executor option when eliminating Node is more valuable than binary size and per-platform checksum management. Woge must never download an unpinned `latest` executable during an ordinary build.

`npm audit` reported zero known vulnerabilities for the locked dependency tree on the recording date. CI installs it with `npm ci --ignore-scripts`; polling watch mode does not require the optional Parcel watcher install script.

## Version policy

The adapter pins one exact tested Tailwind/CLI version and records it in release evidence. Patch updates require extraction, golden-output, watch, source-map and standalone-parity tests. Minor updates additionally review source detection and generated CSS behavior. A major update requires an explicit compatibility decision and migration notes. Applications may choose another version only as an unsupported toolchain override until that matrix is green.

## Reproduction

```shell
cd spikes/tailwind-kotlin
npm ci --ignore-scripts
./validate.sh
./compare-standalone.sh
```

## Primary references

- [Tailwind CLI installation](https://tailwindcss.com/docs/installation/tailwind-cli)
- [Tailwind source detection and dynamic-class guidance](https://tailwindcss.com/docs/detecting-classes-in-source-files)
- [Tailwind theme variables](https://tailwindcss.com/docs/theme)
- [Tailwind 4.3 release](https://tailwindcss.com/blog/tailwindcss-v4-3)
- [Tailwind 4.3.3 release and standalone assets](https://github.com/tailwindlabs/tailwindcss/releases/tag/v4.3.3)
- [Gradle incremental build inputs and outputs](https://docs.gradle.org/current/userguide/incremental_build.html)

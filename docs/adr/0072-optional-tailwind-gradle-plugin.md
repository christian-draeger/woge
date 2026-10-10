# ADR 0072: Ship Tailwind as an optional Gradle plugin with a generated entry file

- Status: Accepted
- Date: 2026-10-11
- Decision owners: Woge maintainers
- Related issues: [#79](https://github.com/christian-draeger/woge/issues/79), [#76](https://github.com/christian-draeger/woge/issues/76), [#152](https://github.com/christian-draeger/woge/issues/152)
- Builds on: [ADR 0017](0017-optional-tailwind-build-adapter.md), [ADR 0068](0068-content-addressed-production-asset-trees.md), [ADR 0070](0070-optional-direct-vite-frontend-adapter.md), [ADR 0071](0071-in-place-stylesheet-updates-in-development.md)

## Context

ADR 0017 decided that Tailwind is an optional build adapter with explicit source roots, static
class names, a locked npm CLI and a pinned standalone executable. This ADR records the concrete
shape: how an application turns it on, how Tailwind finds Kotlin class names, how the stylesheet
reaches development and production, and what is left for later.

## Decision

**A separate plugin, `dev.woge.tailwind`, turns Tailwind on.** Applications without it have no
Tailwind task, setting or dependency. Removing the plugin and the `/tailwind.css` link is the whole
way back to plain CSS; pages, components and actions do not change.

**Woge writes Tailwind's entry file.** The application keeps its own Tailwind CSS (theme, custom
utilities, layers) in `src/main/tailwind/tailwind.css` and does not import Tailwind itself. The
build writes a small entry file:

```css
@import "tailwindcss" source(none);
@import "/app/src/main/tailwind/tailwind.css";
@source "/app/src/main/kotlin";
@source "/app/build/generated/ksp/main/kotlin";
```

Automatic detection is off and every scanned directory is named, as ADR 0017 requires. The default
sources are the main Kotlin and Java directories plus KSP output; `wogeTailwind.sources` adds more,
which is where source-distributed components (#76) will plug in. An input that imports
`tailwindcss` itself is rejected with a clear message, because it would turn automatic detection
back on.

**Dynamic class names fail the build.** Before Tailwind runs, the task scans Kotlin string
literals for a utility prefix followed by `$` or `" +`, for example `"bg-$tone-500"`. Each finding
prints `file:line` and explains the fix: write complete names with `when`, or list them with
`@source inline(...)`. Comment lines are skipped. The check is a guard, not a proof, and can be
turned off with `checkDynamicClasses = false`.

**Executors.** `npm()` is the default and runs `node_modules/.bin/tailwindcss`; a missing install
fails with the exact `npm install` command, and a version other than the supported one prints a
warning. `standalone()` downloads the official executable for the current platform once into the
Gradle user home, checks its pinned SHA-256 and never asks for `latest`. `executable = file(...)`
uses an already installed CLI, for example from Nix or a company mirror. Each Woge release names
one supported Tailwind version (now 4.3.3).

**One stylesheet, three consumers.** The task is cacheable and writes
`build/generated/woge-tailwind/resources/static/tailwind.css`, minified, at a stable path:

- The directory is a resource root, so `processResources` copies it and `wogeDev` serves
  `/tailwind.css` like any other static file.
- A sync task merges `src/main/resources/static` with the Tailwind output, and `wogeAssets`
  hashes that merged tree, so production serves `/_woge/assets/<hash>/tailwind.css` (ADR 0068).
- `wogeDev` also watches the Tailwind input's folder. Saving it is a CSS-only change, so the
  browser swaps stylesheets in place (ADR 0071). Saving Kotlin rebuilds Tailwind as part of the
  normal restart.

Plain CSS stays separate and unchanged. Tailwind uses CSS cascade layers, so application CSS
placed in `@layer components` still loses to a utility on the same element, while unlayered CSS
wins over all of Tailwind. The guide shows both.

## Alternatives considered

- **Let applications write `@import "tailwindcss"` themselves:** rejected because forgetting
  `source(none)` or a source directory silently drops classes in production.
- **Add Tailwind to `dev.woge.spring-boot`:** rejected because plain CSS applications would carry
  the setting and the task, and Ktor applications could not use it.
- **Run Tailwind through Vite (ADR 0070):** rejected for Kotlin sources, because Vite is optional
  and needs Node.js, while the standalone executor does not.
- **Emit source maps now:** deferred. ADR 0017 requires external maps with normalized paths; the
  hashed asset tree has no map rewriting yet.
- **Watch Tailwind continuously with `--watch`:** rejected because `wogeDev` already rebuilds on
  save and owns one build loop.

## Consequences

### Positive

- Two lines of Gradle and one CSS file give a working Tailwind setup, in development and
  production, with or without Node.js.
- Missing classes from runtime-built names show up while building, with file and line.
- Removing Tailwind is a build change only.

### Negative

- The dynamic class check uses patterns and can miss unusual Kotlin expressions.
- The standalone executor only covers macOS, Linux (glibc) and Windows x64, and needs one network
  download per Tailwind version.
- There are no Tailwind source maps yet.

## Follow-up

- Add component source roots or manifests when packaging lands in [#76](https://github.com/christian-draeger/woge/issues/76).
- Add normalized external source maps once the asset pipeline can rewrite map URLs.
- Add musl Linux and Windows ARM executables when release infrastructure tests them.

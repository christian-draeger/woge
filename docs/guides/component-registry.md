# Own your component source

Woge's component registry copies small, useful recipes into your application. They become **your
Kotlin and CSS**, like any other source file. Edit them freely. There is no runtime registry,
hydration step or Node requirement. Spring Boot and Ktor use the same code.

## Install a recipe

Add the optional plugin to your application's `build.gradle.kts`, using your Woge release version:

```kotlin
plugins {
    id("dev.woge.components") version "0.1.0-SNAPSHOT"
}

wogeComponents {
    packageName.set("com.example.ui")
    wogeVersion.set("0.1.0-SNAPSHOT") // Match the Woge dependencies used by this application.
}

dependencies {
    implementation("dev.woge:woge-ui-headless:0.1.0-SNAPSHOT")
}
```

The installer reports required dependencies; it does not rewrite your build script. The binary
[headless primitives](ui-primitives.md) own native semantics and safe output. Installed recipes
compose those primitives and own presentation.

```sh
./gradlew wogeComponents
./gradlew wogeComponentAdd --name=disclosure-card --variant=css --preview
./gradlew wogeComponentAdd --name=disclosure-card --variant=css
./gradlew wogeComponentAdd --name=status-message --variant=css
```

`--preview` shows the file plan, hashes, license and dependencies without writing anything.
Gradle's built-in `--dry-run` only shows the task graph; use `--preview` to see the component plan.
You can supply `--package=com.example.ui` instead of configuring `packageName`.

The installer refuses existing target files; it never silently replaces them. Commit the installed
sources, `woge-components/licenses/` and `woge-components.lock.json`. The lock keeps upstream metadata,
license/provenance and readable pristine snapshots for later comparisons. Do not hand-edit the lock.

| Recipe | Variants | Native HTML / primitive |
| --- | --- | --- |
| `disclosure-card` | `css`, `tailwind` | `details` / `summary`, `disclosure` |
| `status-message` | `css` | `div role="status"` or `role="alert"`, `liveRegion` |

The [canonical recipes](../../registry) and [contract tests](../../testing/woge-component-registry)
are compile-verified. Import the installed extension functions from your package and call them
inside an existing HTML writer:

```kotlin
disclosureCard("Delivery details") {
    p { text("Ships in two days.") }
}
statusMessage() // Start an announcement region empty; fill it through your normal update path.
```

## Plain CSS or Tailwind

Plain CSS files go under `src/main/resources/static/assets/components/`. Link them once in the
initial page head using the normal [stylesheet helper](css-and-assets.md). For example, load
`/assets/components/disclosure-card.css` and `/assets/components/status-message.css`. Spring Boot
serves these static resources; Ktor applications configure their normal static-resource route.
Patches do not inject styles. Production asset hashing remains an application build concern.

Styles are normal `.css` files with IntelliJ diagnostics at their original file and line. You can
edit them directly. Scoped selectors use `data-woge-scope="woge-disclosure-card"` and layer
`woge.components.disclosure-card`. Those names stay stable across installations and updates. This
catalog authors the selectors explicitly; it does not rewrite your CSS. The manifest also supports
explicit global assets. Custom properties such as `--woge-card-border`, `--woge-card-radius` and
`--woge-status-color` let your application theme recipes without changing their semantics.

To choose Tailwind instead, install into a fresh target with:

```sh
./gradlew wogeComponentAdd --name=disclosure-card --variant=tailwind
```

The Tailwind variant installs Kotlin with complete utility class names and no extra stylesheet.
Point your normal [Tailwind build](tailwind.md) at `src/main/kotlin`; the registry declares its Kotlin
candidate file as a Tailwind-specific asset in the standard `utilities` layer. CSS and Tailwind
variants render the same native HTML meaning. Installing a recipe does not install or run Tailwind.
Choose a variant once; changing variant or package later is a manual migration, not an update flag.

## Review an update

After upgrading the Woge plugin (or a reviewed offline mirror), run:

```sh
./gradlew wogeComponentUpdate
./gradlew wogeComponentUpdate --name=disclosure-card --apply
```

The first command writes nothing. It compares three versions: **base** (the last pristine upstream
source), **local** (your current file, including deletion) and **new** (the current registry source).
It lists unchanged files, preserved local edits, safe updates/removals and conflicts with hashes.

- Local unchanged: `--apply` safely replaces or removes the file.
- Upstream unchanged: your local edit or deletion remains untouched.
- Already merged: the tool advances the baseline without rewriting the file.
- Both changed differently: the entire item is deferred; your files and its lock entry stay unchanged.

For conflicts, `--apply` writes `.woge-base`, `.woge-local` and `.woge-new` snapshots under
`build/woge-components/update/<item>/<installed-path>`. A missing snapshot means that version of the
file does not exist. Compare them in your IDE's three-way merge view or ordinary diff tool. Merge
upstream changes into your application code, keeping the customization you want. Then explicitly
acknowledge that reviewed result:

```sh
./gradlew wogeComponentUpdate --name=disclosure-card --apply --accept-local
```

This accepts **all reviewed conflicts in that named item** and advances its pristine baseline;
it does not replace locally customized files. Do not use it until you have reviewed every conflict.
Run your application's tests and commit the resulting source and lock changes. Keep version control
backups: preflight checks protect local edits, but disk failures are not a cross-file transaction.

## Publish or review a catalog change

[`registry/schema.json`](../../registry/schema.json) defines the versioned format. Every item includes
SPDX license, source repository/path/revision, Woge compatibility bounds, minimum Kotlin version,
module/item dependencies, used primitives, design tokens, explicit assets and per-file hashes.
Each variant has ordinary installable sources and separate executable Kotlin contract tests. Template
Kotlin uses the valid package `dev.woge.registry.template`; installation substitutes your package.
An item's content-tree revision hashes sorted `source-path sha256` lines; release tooling can instead
record a Git source commit. Source hashes are integrity metadata, not publisher signatures.

When editing a recipe, bump its immutable version, format it, refresh the file/provenance hashes and
run the focused checks:

```sh
./gradlew :woge-component-registry:ktlintFormat
python3 scripts/update-component-registry-hashes.py
./gradlew :woge-gradle-plugin:check :woge-component-registry:check
```

Normal `check` compiles and tests every item and variant and validates the packaged index. New
variants must provide tests too. Humans and coding agents follow this same workflow and public APIs.
Do not copy behavior that an existing headless primitive already owns.

For a trusted local mirror, set `wogeComponents.registryDirectory` to its directory (containing
`index.json` and declared relative sources). The bundled schema remains authoritative. There is no
remote download or executable template support. Never install an unreviewed catalog: Kotlin is
application code. The current compatibility interval is Woge `>= 0.1.0, < 0.2.0`, with Kotlin 2.4.10
or newer. The task enforces Woge bounds; use the declared Kotlin minimum in your application build.

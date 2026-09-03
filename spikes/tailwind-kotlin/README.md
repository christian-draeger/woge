# Tailwind with Kotlin templates

Tailwind is optional in Woge. If you use it, utility classes stay ordinary HTML class names:

```kotlin
val layoutClassCandidates =
    "project-card grid gap-4 p-card md:grid-cols-2 hover:shadow-lg"
```

Runtime choices map to complete strings so Tailwind can see every possible class while scanning source:

```kotlin
val toneClassCandidates = when (tone) {
    ProjectTone.INFO -> "bg-brand-500 text-white"
    ProjectTone.WARNING -> "bg-amber-100 text-amber-950"
}
```

Do not build a utility name such as `"bg-${tone}-500"`. Tailwind scans text rather than executing Kotlin, so that class would be missing from production CSS. This spike adds a source-located Gradle check for common dynamic constructions and demonstrates Tailwind's explicit `@source inline(...)` safelist for exceptional candidates.

The build keeps two separate assets:

- `application.css` is copied byte for byte and remains governed by Woge's standards-native CSS contract.
- `tailwind.min.css` is generated from `tailwind.css`, Kotlin sources, generated descriptors and source-distributed components.

Both are normal linked stylesheets. Neither affects Woge component identity or patching.

## Run the spike

```shell
cd spikes/tailwind-kotlin
npm ci --ignore-scripts
./validate.sh
```

For continuous development CSS rebuilds:

```shell
../spring-html-htmx-baseline/gradlew -p . tailwindCssWatch
```

The task uses polling so the optional Parcel watcher install script is not required. Browser reload or HMR belongs to the consuming application dev server, not the Woge runtime.

To compare the npm CLI with Tailwind's official standalone executable, including its published SHA-256 digest:

```shell
./compare-standalone.sh
```

See the [evidence](evidence.md) and [integration contract](../../docs/architecture/tailwind-integration.md) for measured results and production boundaries.

# Tailwind CSS

Tailwind is optional. Woge pages work with plain CSS, and nothing in Woge's HTML, component or
action APIs knows about Tailwind. If you want utility classes, one Gradle plugin builds a
Tailwind stylesheet from the class names in your Kotlin code.

The complete, tested setup is the [Tailwind example](../../examples/tailwind-spring-boot/README.md).

## Turn it on

Add the plugin next to the Woge Spring Boot plugin (it also works with Ktor):

```kotlin
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.google.devtools.ksp")
    id("org.springframework.boot")
    id("dev.woge.spring-boot")
    id("dev.woge.tailwind")
}
```

Install the supported Tailwind version with npm:

```shell
npm install --save-dev --save-exact tailwindcss@4.3.3 @tailwindcss/cli@4.3.3
```

Create `src/main/tailwind/tailwind.css`. It can be empty. Do **not** write
`@import "tailwindcss"`; Woge adds it for you, together with the list of folders to scan.

Link the stylesheet in your page:

```kotlin
head {
    stylesheet(assets.url(applicationUrl("/tailwind.css")))
}
```

That is all. `./gradlew wogeDev` serves `/tailwind.css`, and production builds serve it under a
content-hashed, long-cached URL like every other [static asset](production-assets.md).

## No Node.js? Use the standalone executable

```kotlin
wogeTailwind {
    standalone()
}
```

Woge downloads the official Tailwind executable for your computer once, checks it against a pinned
checksum and keeps it in the Gradle cache. Behind a firewall, or with Nix, point to your own copy
instead:

```kotlin
wogeTailwind {
    executable = file("/opt/tailwind/tailwindcss")
}
```

## Write complete class names

Tailwind reads your Kotlin files as text and keeps only the classes it can see. A class name that is
put together at runtime, such as `"bg-$tone-500"`, is invisible, so it would be missing in
production. Woge stops the build instead:

```text
Tailwind cannot see class names that are built at runtime:
  src/main/kotlin/example/Badge.kt:4: "bg-$tone-500"
```

Pick complete names with `when`:

```kotlin
fun badgeClasses(tone: Tone): String =
    when (tone) {
        Tone.OK -> "bg-green-100 text-green-800"
        Tone.DOWN -> "bg-red-100 text-red-800"
    }
```

If you really need names that only exist at runtime, list them in your Tailwind CSS with
`@source inline("bg-red-500 bg-green-500");`. To turn the check off, set
`wogeTailwind { checkDynamicClasses = false }`.

## Design tokens, dark mode and accessibility

Keep your tokens as normal CSS custom properties and hand them to Tailwind with `@theme inline`.
Media queries switch the values, so dark mode and high contrast need no JavaScript:

```css
:root { --woge-surface: #fff; --woge-ink: #1f2933; }

@media (prefers-color-scheme: dark) {
  :root { --woge-surface: #111827; --woge-ink: #f5f7fa; }
}

@media (prefers-contrast: more) {
  :root { --woge-muted: var(--woge-ink); }
}

@theme inline {
  --color-surface: var(--woge-surface);
  --color-ink: var(--woge-ink);
}
```

Now `bg-surface text-ink` follows the user's settings. For animations, use Tailwind's
`motion-safe:` and `motion-reduce:` variants; they are `prefers-reduced-motion` media queries.

## Mix with plain CSS

Keep ordinary CSS in `src/main/resources/static/` and link it after `/tailwind.css`. Woge never runs
it through Tailwind, so nesting, container queries and anything else your browsers support stay
exactly as written.

Tailwind puts its styles in [cascade layers](https://developer.mozilla.org/en-US/docs/Web/CSS/@layer)
(`theme`, `base`, `components`, `utilities`). Choose where your CSS goes:

- Inside `@layer components { ... }`: a utility class on the same element still wins. Good for
  component styles you want to tweak with utilities.
- Without a layer: your CSS wins over all of Tailwind.

## While developing

`./gradlew wogeDev` watches your Kotlin code and the folder with your Tailwind CSS.

- Saving `tailwind.css` rebuilds the stylesheet and updates it in the open tabs without a page
  refresh ([ADR 0071](../adr/0071-in-place-stylesheet-updates-in-development.md)).
- Saving Kotlin rebuilds both the application and the stylesheet, so new classes appear after the
  usual refresh.

## Settings

| Setting | Default | Meaning |
| --- | --- | --- |
| `input` | `src/main/tailwind/tailwind.css` | Your Tailwind CSS |
| `outputPath` | `tailwind.css` | URL path of the stylesheet below `static/` |
| `npm()` / `standalone()` | `npm()` | Which Tailwind CLI runs |
| `nodeProjectDirectory` | the project folder | Where `package.json` and `node_modules` are |
| `executable` | not set | Use this Tailwind CLI instead |
| `sources` | Kotlin, Java and KSP output | Folders Tailwind scans; add more with `sources.from(...)` |
| `checkDynamicClasses` | `true` | Fail on class names built at runtime |

The task is `./gradlew wogeTailwind`. It runs automatically before resources are packaged.

## Remove Tailwind

Delete the plugin line, the `/tailwind.css` link and `src/main/tailwind/`. Your pages, components
and actions stay the same; class attributes are ordinary strings either way.

The decisions behind this guide are [ADR 0017](../adr/0017-optional-tailwind-build-adapter.md) and
[ADR 0072](../adr/0072-optional-tailwind-gradle-plugin.md).

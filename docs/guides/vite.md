# Vite

Vite is optional. Plain HTML and CSS pages never need Node.js, and nothing in Woge's HTML, component
or action APIs knows about Vite. Use the `dev.woge.vite` plugin when part of your page needs a real
frontend build: TypeScript, npm packages, CSS imported from JavaScript, or Vite plugins.

The complete, tested setup is the [Vite example](../../examples/vite-spring-boot/README.md). The
decision behind it is [ADR 0070](../adr/0070-optional-direct-vite-frontend-adapter.md).

## Turn it on

Add the plugin next to the Woge Spring Boot plugin (it also works with Ktor's `dev.woge.application`):

```kotlin
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.google.devtools.ksp")
    id("org.springframework.boot")
    id("dev.woge.spring-boot")
    id("dev.woge.vite")
}

dependencies {
    implementation("dev.woge:woge-vite:$wogeVersion")
}
```

Install Vite in the project folder:

```shell
npm install --save-dev vite
```

Put your entry module in `src/main/frontend/main.ts`. Everything else is imported from there, as in
any Vite project:

```ts
import "./main.css";

document.querySelector("#show-chart")?.addEventListener("click", async () => {
  const { drawChart } = await import("./chart.ts"); // loaded only when needed
  drawChart(document.querySelector("#visits")!);
});
```

Create one `ViteAssets` bean and load the entry in the page's `<head>`:

```kotlin
@Bean
fun viteAssets(assets: AssetUrls): ViteAssets = ViteAssets(assets)
```

```kotlin
head {
    viteEntry(vite, ViteEntry("main.ts"))
}
```

`viteEntry` writes ordinary `<script type="module">` and `<link rel="stylesheet">` tags. Pass
`nonce = ...` when your page uses a nonce-based Content Security Policy.

## What happens when

| | Production (`./gradlew build`, `bootJar`) | Development (`./gradlew wogeDev`) |
| --- | --- | --- |
| Who runs Vite | The `wogeVite` task runs `vite build` | `wogeDev` starts the Vite dev server on `127.0.0.1:5173` |
| What the page loads | `/vite/main.js` and `/vite/main.css`, content-hashed like every static file | `@vite/client` and `main.ts` straight from the Vite dev server |
| Caching | Long-lived, immutable asset URLs ([production assets](production-assets.md)) | No caching; Vite serves fresh modules |

Vite writes stable file names (`main.js`, `chunks/chart.js`). Woge adds the content hash to the whole
asset folder, so chunk imports stay relative and still work. A second build without changes is
up to date and does not run Vite.

## Two reload owners, no overlap

- **Vite** owns files in `src/main/frontend`: an edit there is a hot update in the browser. Woge does
  not rebuild or restart anything.
- **Woge** owns Kotlin: an edit in a page or route rebuilds and restarts the application, then reloads
  the browser once the new version is ready.

Kotlin files are never Vite inputs, and frontend files are never Woge build inputs.

## Settings

The defaults fit most projects. All of them are optional:

```kotlin
wogeVite {
    frontendDirectory = layout.projectDirectory.dir("src/main/frontend")
    entries = listOf("main.ts", "admin.ts") // file names in frontendDirectory
    outputPath = "vite"                     // served as /vite/main.js
    devPort = 5173                          // the Vite dev server port during wogeDev
    sourceMaps = false                      // true writes .map files next to the production files
    node = "node"                           // or an absolute path to a Node.js executable
}
```

Vite plugins and other options go into your normal `vite.config.ts` (or `.js`, `.mjs`, ...) in the
project folder. Woge only decides the root folder, the entry files, the output folder and the file
names, so its asset hashing keeps working.

## Content Security Policy

During `wogeDev` on Spring Boot, Woge adds the Vite dev server (`http://127.0.0.1:5173`) and its
hot-update socket (`ws://127.0.0.1:5173`) to your policy's `script-src`, `style-src` and
`connect-src`. With a nonce, `viteEntry` also writes `<meta property="csp-nonce">`, so the styles the
Vite client injects get the same nonce. Production responses are never changed.

Ktor has no automatic policy rewrite yet. If your Ktor application sends a strict policy, allow the
Vite origin yourself while developing.

## Troubleshooting

- **"Vite is not installed"**: run `npm install --save-dev vite` (or `npm ci`) in the project folder.
- **"Could not start Node.js"**: install Node.js 20.19 or newer, or set `wogeVite { node = "..." }`.
- **"Port 5173 ... is already in use"**: stop the other dev server or choose another `devPort`.
- **The page has no styles in production**: the stylesheet tag is only written when the entry imports
  CSS. Check that `main.ts` has `import "./main.css"`.

## Remove Vite again

Delete the `dev.woge.vite` plugin, the `woge-vite` dependency, the `viteEntry` calls and
`src/main/frontend`. Plain CSS and scripts in `src/main/resources/static` keep working unchanged.

For Tailwind with class names in Kotlin, use the [Tailwind plugin](tailwind.md) instead of
`@tailwindcss/vite`: Vite never reads Kotlin files.

# Vite frontend evidence

Recorded on 2026-10-11 on macOS arm64 with Node.js 26.3.0, Vite 8.3.4 (Rolldown), Tailwind CSS and
`@tailwindcss/vite` 4.3.3, Gradle 8.14.4 and `dev.opensavvy.vite.base` 0.9.2. Timings are medians
of five runs from `measure.mjs`, unless stated otherwise.

## Result

Vite is a good optional tool for applications that want TypeScript, npm packages and module HMR.
It must stay optional and outside Woge's core, and Woge should invoke Vite directly rather than
through the OpenSavvy Gradle plugins. Plain HTML/CSS applications keep the Node-free path from
ADRs [0016](../../docs/adr/0016-standards-native-css-authoring.md) and
[0068](../../docs/adr/0068-content-addressed-production-asset-trees.md).

Kotlin template changes must not go through Vite. The Woge dev session already owns those
reloads.

## Two variants of the same page

| Property | Gradle only (`src/plain`) | Vite (`src/frontend`) |
| --- | ---: | ---: |
| Node.js required | No | Yes |
| Installed dependencies | 0 | 33 packages, 46.3 MB `node_modules` (lockfile lists 78 entries including other platforms' native binaries) |
| Production build, warm Gradle daemon with configuration cache | 0.39 s (`Sync`, up to date or changed) | 0.52 s through `ViteExec`; 0.13 s for `vite build` alone |
| Up-to-date build | Skipped by Gradle | Runs again every time (see below) |
| Dev server start to first module | No server | 0.14 s listening, 0.16 s first modules |
| Dev server memory | 0 | 141 MB resident |
| CSS change in the browser | Woge reload today; stylesheet swap in [#152](https://github.com/christian-draeger/woge/issues/152) | 16 ms to the HMR message |
| TypeScript change | Not applicable (no compiler) | 17 ms to the HMR message |
| Plain CSS output | Byte for byte | Rewritten (see below) |
| Output size | Source size | `main.js` 1,770 B (876 B gzip), `main.css` 4,939 B (1,713 B gzip), lazy chunk 64 B |

Kotlin/JS output was not included. It is not practical for this question: Woge does not need
Kotlin/JS, and the OpenSavvy Kotlin plugin targets Kotlin/JS applications, not server-rendered
pages.

## Development loop

The Vite dev server took 0.14 seconds to listen, and HMR messages arrived 15–17 ms after a file
write. It stopped within 30 ms on `SIGTERM`. A parent process, such as `wogeDev`, can therefore
own Vite as one more child process.

| Change | Vite message | Meaning |
| --- | --- | --- |
| Plain CSS imported by the entry | `js-update` | Stylesheet replaced without page reload |
| TypeScript module | `js-update` | Module replaced; the entry accepts its own updates |
| Kotlin file scanned by `@tailwindcss/vite` | `full-reload` | Whole page reloaded immediately |

The last row is by design in `@tailwindcss/vite`: a changed template file reloads the page. In a
Woge application that reload arrives about 15 ms after saving, while Woge is still compiling and
restarting the server. The browser reloads against the old server, or against no server at all,
and then reloads again when Woge's dev channel announces the new generation
([ADR 0041](../../docs/adr/0041-orchestrator-owned-spring-reload-and-sse-channel.md)). So Kotlin
sources must not be Vite inputs. Tailwind classes in Kotlin go through the Woge Tailwind adapter
([ADR 0017](../../docs/adr/0017-optional-tailwind-build-adapter.md), [#79](https://github.com/christian-draeger/woge/issues/79)),
whose output Woge announces after a successful build.

A Woge server restart does not touch Vite: its WebSocket is a separate connection to its own
origin. Changing `vite.config.mjs` restarts only Vite.

## Single origin and proxying

No proxy was built. Vite's documented backend integration lets the page load
`http://localhost:5173/@vite/client` and the entry module directly from the Vite origin. Vite needs
`server.cors` for the application origin and `server.origin` so asset URLs point back to Vite.

This keeps ADR 0041's "no reverse proxy" rule. The page's Content-Security-Policy must allow the
Vite origin for `script-src`, `style-src` and `connect-src` (including `ws:`) in development only.
`DevelopmentContentSecurityPolicy` already does exactly this for the Woge dev client's own loopback
origin.

## Production output

- **Determinism:** five builds produced one output digest.
- **Content hashing:** Vite can hash file names and write `.vite/manifest.json`. The spike instead
  emits stable names (`main.js`, `chunks/chart.js`, `main.css`) under one directory. Lazy chunks
  are imported with relative URLs (`import("./chunks/chart.js")`), so the files work unchanged
  under Woge's content-addressed `/_woge/assets/<tree-hash>/` tree. Then there is one hashing
  scheme, one manifest and one URL API (`AssetUrls`) for every asset.
- **Minification:** on by default for JavaScript and CSS.
- **CSP:** the output is external module scripts without `eval` or `new Function`, so it works
  with `script-src 'self'`. Vite's preload helper reads a nonce from `meta[property=csp-nonce]` if
  present.
- **Source maps:** off by default. With `build.sourcemap: true` they are external files whose
  `sources` are relative to the output directory. When the output stays inside the project they
  contain no checkout path. A temporary output directory outside the project leaked the absolute
  checkout path, so the adapter must keep its output inside the build directory.

## Vite rewrites CSS it processes

The plain stylesheet imported through Vite did not survive byte for byte. Native nesting
(`& h2`) was lowered to `.card h2` for the default browser target. The plain and Tailwind
stylesheets were merged into one `main.css`. That is reasonable for a Vite project, but it is not
ADR 0016's standards-native contract. Plain CSS that the application wants preserved stays in
Woge's static tree and is linked normally. CSS imported from a Vite entry is Vite's to process.

## OpenSavvy Gradle plugins

`dev.opensavvy.vite.base` 0.9.2 depends only on the Kotlin standard library. It offers a Gradle DSL
mirror of the Vite config (`WriteConfig`) and an exec task (`ViteExec`). Findings:

- The configuration cache stored and reused the entry without problems.
- `ViteExec` is marked cacheable but declares no outputs. It ran again on every build, even with
  nothing changed.
- Its inputs cover the whole frontend directory. A sibling `Sync` task writing below that
  directory failed Gradle's implicit-dependency validation when both ran in one build.
- It does not install Vite or Node. The project still needs its own `package.json` and lockfile.
- `serve` as a Gradle task blocks the build. In Woge the dev session, not Gradle, owns long-running
  processes.
- `dev.opensavvy.vite.kotlin` targets Kotlin/JS and Kotlin Multiplatform browser applications,
  which Woge pages are not.

A small Woge-owned `Exec`-based task with declared inputs (sources, `package-lock.json`, Vite
config) and one declared output directory is simpler and fixes the up-to-date and overlap problems.

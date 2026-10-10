# ADR 0070: Offer Vite as an optional, directly invoked frontend adapter

- Status: Accepted
- Date: 2026-10-11
- Decision owners: Woge maintainers
- Related issues: [#142](https://github.com/christian-draeger/woge/issues/142), [#79](https://github.com/christian-draeger/woge/issues/79), [#152](https://github.com/christian-draeger/woge/issues/152), [#205](https://github.com/christian-draeger/woge/issues/205)
- Builds on: [ADR 0016](0016-standards-native-css-authoring.md), [ADR 0017](0017-optional-tailwind-build-adapter.md), [ADR 0041](0041-orchestrator-owned-spring-reload-and-sse-channel.md), [ADR 0068](0068-content-addressed-production-asset-trees.md)

## Context

Some Woge applications want TypeScript, npm packages and fast module hot updates. Vite is the
common tool for that. Plain HTML/CSS applications must stay free of Node.js, and Woge must not grow
a second build system or reload protocol.

The question was whether Woge should use the OpenSavvy Gradle plugins
(`dev.opensavvy.vite.base`, `dev.opensavvy.vite.kotlin`), call Vite directly, or not integrate
Vite at all. The [Vite frontend spike](../../spikes/vite-frontend/evidence.md) built the same page
with and without Vite and measured build time, dev-server start, HMR latency, memory, Node
footprint, configuration-cache behavior, output hashing and source maps.

## Decision

Vite is an **optional frontend adapter**. Woge core, the HTML DSL, host adapters and the browser
patch runtime do not know about Vite. A Woge application without Vite needs no Node.js, and removing
Vite changes no page or action code.

**Woge calls Vite directly.** The adapter runs the project's own locked `vite` from `node_modules`.
It does not use the OpenSavvy plugins. Their exec task declares no outputs, so it never becomes
up to date. Its coarse inputs clash with sibling outputs, and its config DSL duplicates
`vite.config.*`. The application keeps a normal `package.json`, lockfile and Vite config that web
developers already know.

**Production:** a Gradle task runs `vite build` with declared inputs (frontend sources, lockfile,
Vite config) and one output directory inside the build directory. Vite emits stable file names
with relative chunk imports. The output joins the static tree, and `wogeAssets` (ADR 0068)
content-addresses it like any other asset. So there is one hashing scheme, one manifest and one
`AssetUrls` API. Source maps stay off by default; when enabled they are external files whose
paths are relative to the project.

**Development:** `wogeDev` starts the Vite dev server as one more child process and stops it with
the session. There is no proxy (ADR 0041): in development the page loads Vite's client and the
entry module straight from the Vite origin. The development Content-Security-Policy adds that
origin, as `DevelopmentContentSecurityPolicy` already does for the Woge dev client.

**Who reloads what:**

- Vite owns hot updates of modules in its graph: TypeScript, JavaScript and CSS imported from the
  entry. Woge does not duplicate this.
- Woge owns server code, Kotlin templates and server restarts. It reloads the page only after a
  successful build and restart (ADR 0041).
- Kotlin sources are never Vite inputs. `@tailwindcss/vite` reloads the page as soon as a scanned
  template changes, about 15 ms after saving, long before the new server is ready. Tailwind classes
  written in Kotlin use the Woge Tailwind adapter (ADR 0017, #79) instead.

**CSS:** Vite rewrites the CSS it processes; it lowered native nesting and merged stylesheets in the
spike. That is Vite's job for CSS imported from a Vite entry. Plain stylesheets the application
wants kept byte for byte stay in Woge's static tree and are linked normally (ADR 0016).

Kotlin/JS is out of scope for this adapter.

## Alternatives considered

- **Use `dev.opensavvy.vite.base`:** rejected. It works with the configuration cache but reruns
  every build, fails Gradle validation next to sibling outputs, mirrors the Vite config in a Gradle
  DSL and still requires the project to install Vite.
- **Use `dev.opensavvy.vite.kotlin`:** rejected. It targets Kotlin/JS browser applications, not
  server-rendered pages.
- **Make Vite the default asset pipeline:** rejected. It would require Node.js for every
  application and would rewrite standards-native CSS.
- **Keep Vite's own hashed names and manifest:** rejected. Two manifests and two URL schemes for one
  page add work and failure modes. Stable names under the content-addressed tree are enough.
- **Proxy the page through Vite or Vite through Woge:** rejected for now. ADR 0041 builds no proxy
  for M1, and Vite's backend integration works across two loopback origins.
- **Do not support Vite:** rejected. TypeScript and npm-module workflows are common, and the
  boundary is small when Woge only starts the process and packages its output.

## Consequences

### Positive

- Plain HTML/CSS applications stay Node-free, unchanged.
- Vite users keep standard Vite configuration, plugins and HMR.
- One asset URL API and one cache policy cover Vite output and static files.
- Reload responsibilities do not overlap, so the browser never reloads against a server that is
  still restarting.

### Negative

- Vite projects need Node.js, about 46 MB of dependencies and about 140 MB for the dev server.
- Development uses a second loopback origin and a widened development CSP.
- Any change in the asset tree, Vite output included, changes every hashed asset URL (ADR 0068).
- `@tailwindcss/vite` with Kotlin sources is unsupported; those projects use the Gradle Tailwind
  adapter.

## Follow-up

- Implement the optional adapter: a cacheable `vite build` task feeding `wogeAssets`, Vite as a
  `wogeDev` child process, dev-only origin and CSP wiring, and an HTML helper that emits the dev
  or production entry tags, in [#205](https://github.com/christian-draeger/woge/issues/205).
- Keep Tailwind for Kotlin sources in the Gradle adapter ([#79](https://github.com/christian-draeger/woge/issues/79)).
- Keep plain stylesheet hot updates in the Woge dev channel ([#152](https://github.com/christian-draeger/woge/issues/152)).

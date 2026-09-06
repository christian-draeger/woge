# Install and deploy the fallback browser client

The fallback client is a small ES module that applies Woge patch streams to server-rendered HTML. It
does not render your page, provide components, manage CSS or require hydration. Choose one install
path; do not load both on the same page.

## Use a frontend asset pipeline

Install the package with the package manager already used by your application:

```shell
npm install @woge/fallback-client
```

Import it from application JavaScript or TypeScript. A bundler resolves the public package entry and
can fingerprint the resulting application asset:

```js
import {
  createWogePatchRuntime,
  WOGE_PATCH_PROTOCOL_VERSION,
} from "@woge/fallback-client";

const response = await fetch("/projects/woge/woge-patches", {
  headers: {
    Accept: `application/vnd.woge.patch-stream; version=${WOGE_PATCH_PROTOCOL_VERSION}`,
  },
});

if (!response.ok || !response.body) throw new Error(`Patch request failed: ${response.status}`);
await createWogePatchRuntime(document).applyPatchStream(response.body);
```

The package has no runtime dependencies or CSS files. `dist/manifest.json` records the package and
patch-protocol versions, output hashes, sizes and the bundled module's SRI value. TypeScript
declarations are exported from the same package entry.

## Use Spring Boot without Node

Add the JVM web-asset adapter next to the Woge server adapter:

```kotlin
dependencies {
    implementation("dev.woge:woge-spring-boot-starter:<woge-version>")
    runtimeOnly("dev.woge:woge-fallback-client-assets:<woge-version>")
}
```

Spring Boot serves the classpath modules below `/assets/woge/`. Your own small application module can
import the public entry directly:

```js
import { createWogePatchRuntime } from "/assets/woge/index.js";
```

This is the path used by the maintained reference application. The asset artifact itself is
framework-neutral: a Ktor application may expose the same classpath directory through its normal
static-resource configuration. The server framework still owns routing and cache policy.

For a plain static deployment without a bundler, either vendor the npm package's single
`dist/woge-fallback.js` file at a versioned URL or serve the JVM artifact's complete
`static/assets/woge` directory. Never copy only `index.js` from the unbundled form because its module
imports are relative.

## Keep browser and server versions aligned

Install the npm or JVM browser artifact with the same Woge release as `woge-protocol`. The browser
exports its protocol version so application requests can advertise the matching media type. It also
validates the stream preamble and every patch's metadata before DOM mutation; an incompatible stream
fails closed instead of being partially applied.

Artifact versions and wire-protocol versions are different concepts. A patch release may improve
diagnostics without changing the protocol. Do not infer protocol compatibility by parsing the npm or
Maven version string.

## Set production asset policy

Prefer a content-hashed public URL for a bundled module. Serve that immutable file with a long-lived
policy such as `Cache-Control: public, max-age=31536000, immutable`. HTML, import maps, bootstraps and
the package manifest select an asset version, so serve those with revalidation rather than the same
immutable policy. Patch-stream responses contain page-specific state and remain `Cache-Control:
no-store`.

Source maps do not execute in the browser, but they expose readable implementation source to anyone
who can fetch them. Publish maps when that debugging tradeoff is acceptable, or keep them in an
authenticated error-monitoring pipeline. Removing the `.map` file does not change runtime behavior.

A same-origin external module works with a strict policy such as `script-src 'self'`; the client does
not use inline scripts, `eval` or dynamic code generation. Add nonces or hashes only for
application-owned inline bootstraps. If the bundled module is loaded directly with a `script` tag,
copy the `integrity` value from `manifest.json` to its `integrity` attribute. Cross-origin SRI also
requires compatible CORS headers. Imported child modules are separate fetches, so prefer the single
npm bundle when SRI must cover the complete runtime graph.

The runtime does not impose a CSS policy. Plain CSS, CSS Modules, Tailwind and component-owned styles
continue through the application's normal asset pipeline.

Next, read [Apply Replace patches in the browser](browser-replace-runtime.md) for the page/region
contract and lifecycle events.

# Install and deploy the fallback browser client

The fallback client is a small ES module that applies Woge patch streams to server-rendered HTML. It
does not render your page, provide components, manage CSS or require hydration. Choose one install
path; do not load both on the same page.

## Use a frontend asset pipeline

> The npm package `@woge/fallback-client` is not published yet. Until then, use the Spring Boot path
> below; it needs no Node.js.

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

const patchUrl = document.body.dataset.wogePatchUrl;
if (!patchUrl) throw new Error("The document has no deferred patch URL");
const response = await fetch(patchUrl, {
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

## Opt in to action-form enhancement

The client also exports `installWogeActionForms(document, runtime)`. It is not installed by importing
the module. Use the same runtime that owns the document's regions:

```js
import { createWogePatchRuntime, installWogeActionForms } from "/assets/woge/index.js";

const runtime = createWogePatchRuntime(document);
const forms = installWogeActionForms(document, runtime);
// Call forms.dispose() when your application stops owning this document.
```

Keep the form's normal method, action and encoding. Add explicit opt-in attributes using the HTML DSL:

```kotlin
actionForm(CreateTaskAction, attributes = {
    data("woge-action", "")
    data("woge-status", "task-status")
    data("woge-alert", "task-alert")
    data("woge-failure-message", "The result is unknown. Check before submitting again.")
}) {
    input(attributes = { attribute("name", "title") })
    button { text("Create task") }
}
p(attributes = {
    attribute("id", "task-status")
    attribute("role", "status")
}) {}
p(attributes = {
    attribute("id", "task-alert")
    attribute("role", "alert")
}) {}
```

Render a verified CSRF field as part of the application's host security integration. The opt-in
does not grant authorization or verify CSRF. Mark the success status as an ordinary registered
patch region if the server updates it; keep live-region elements in the document shell. The
application chooses one success message, not one message per patch.

Only same-origin UTF-8 URL-encoded POSTs targeting the current window are enhanced. Submitter
overrides, repeated values, browser validation and `formdata` events remain meaningful. Files,
multipart, image submitters, other methods and external destinations stay native.

An enhanced endpoint must explicitly negotiate the advertised
`application/vnd.woge.patch-stream; version=1` and return a compatible stream. Alternatively, a
successful response with `Woge-Navigate: /tasks` requests a normal same-origin GET navigation.
Ordinary HTML or redirect responses are not silently treated as patches. Woge action bindings on
MVC, WebFlux and Ktor translate an application-owned 303 redirect into this navigation response for
the explicit current-version patch request. Native requests still receive their original 303.
External and POST-preserving 307/308 redirects are never translated. Server actions can return
[prepared typed region updates](typed-actions.md#update-typed-regions-after-an-action).

The form is busy only while its request runs. Its submitter uses `aria-disabled`, not the HTML
`disabled` attribute, and success keeps focus in place. Failure restores those attributes, retains input,
writes the form's safe failure message into its existing alert, and emits `woge:action-error`
with a diagnostic `detail.code`. The client never retries a POST, including after an uncertain
network result. Application recovery must first establish whether the mutation happened.
`forms.dispose()` cancels owned requests and restores busy state without submitting again.

For accessible validation, also set `data-woge-error-summary` to your document-owned summary ID.
`actionValidationUpdates` returns HTTP 400 patches with `Woge-Validation` naming that exact ID.
After the complete valid stream, the client focuses its `tabindex="-1"` summary, without an extra
status or alert announcement. Wrong summary IDs, missing focus targets and incompatible responses
fail closed; stale or cancelled responses stay silent. The summary must not be a live region.

The policies are recorded in [ADR 0056](../adr/0056-explicit-action-form-enhancement.md) and
[ADR 0058](../adr/0058-shared-accessible-form-errors.md).

For rolling deployments, align the npm client with the server release and retain both versions of
fingerprinted assets during the rollout. See the [compatibility and upgrades guide](compatibility-and-upgrades.md).

# Local islands: one widget, not a hydrated page

An island adds local behavior to **one** useful server-rendered root. Your links, form, validation and
other page content still work before JavaScript loads or when a module fails. For example, the
[reference notes form](../../examples/reference-application/shared/src/main/kotlin/dev/woge/example/notes/NoteIsland.kt)
shows native required/maxlength validation and a server hint; a lazily loaded
[DOM adapter](../../examples/reference-application/shared/src/main/resources/static/assets/note-counter.js)
calls compiled shared Kotlin rules for immediate validation and draft feedback. Server validation remains authoritative.

## Declare the public data and deployment assets

Kotlin's `IslandType<Int>` means this serializer accepts an integer, not an arbitrary server object.
Select only public fields. The minimal props wire format is a versioned JSON object of **strings**;
parse numbers and validate field meanings inside your module. Woge does not evaluate props as code.

```kotlin
import dev.woge.html.*

val counter = IslandType<Int>("note-counter", propsVersion = 1) { maximum ->
    IslandProps(mapOf("maxLength" to maximum.toString()))
}
val islands = IslandRegistry(IslandModule(counter, "/assets/note-counter.js", assetVersion = "1"))

// Inside the typed page DSL:
head {
    islandManifest(islands) // Once, only in the original document head.
    islandLoader()          // External /assets/woge/islands.js; only on pages using islands.
}
body {
    island(counter, 120, key = "note-counter") {
        label { text("Note") }
        input { attribute("name", "note"); attribute("maxlength", "120") }
        p { text("Up to 120 characters. Checked on the server.") }
    }
}
```

Use the compiled reference source for complete, accessible labels and status markup. Woge generates
the manifest and inert root-local props; you do not hand-build JSON or HTML. Props live in an invisible
`template[data-woge-island-props]`, not an executable script. All scripts are still forbidden in
patches. The root contains a **type name, never a module URL**. Only the original head manifest can
choose imports; patch-supplied URL attributes are ignored. An unknown type keeps its fallback.

Module paths must start with one `/`, contain only ASCII letters, digits, `_`, `-`, `/`, and the
`.js`/`.mjs` suffix, with no query, fragment, encoding, traversal or origin. The registry appends
`?v=<assetVersion>`. Use a new immutable version/hash for every changed module. Changing the allowlist
requires a complete page navigation, not a region patch. Do not leak secrets or server-only models.

## Write a normal ES module

Export the configured function (`mount` by default):

```javascript
export function mount(root, props, { signal, propsVersion }) {
  if (propsVersion !== 1) throw new Error("Unsupported props");
  const input = root.querySelector("input");
  const hint = root.querySelector("p");
  function render() { hint.textContent = `${input.value.length} / ${props.maxLength}`; }
  input.addEventListener("input", render, { signal });
  render();
  return {
    update(next) { props = next; render(); },
    dispose() { input.removeEventListener("input", render); },
  };
}
```

Validate field meanings before changing DOM. Mount **synchronously**, only inside `root`; do not
return a Promise. Async application work must check the supplied abort signal. Use signal-bound
listeners and dispose timers/subscriptions/third-party resources explicitly. `update` and `dispose`
are optional. An import failure leaves the exact fallback; a synchronous mount failure restores
cloned fallback children and aborts signal-bound resources. Arbitrary side effects, selected files
and asynchronous mutations are not rollback-safe: build widgets so their initial mount cannot fail
after irreversible work. Never intercept the containing form's native/server validation casually.

`visible` is the default strategy: the module loads when its root enters the viewport. Use
`attributes = { islandLoading(IslandLoading.EAGER) }` for immediately needed behavior. Without IntersectionObserver it
loads eagerly. The manager caches imports, mounts once per root and does not retry failed imports.

## Patches and local state

- No key: before replacement Woge disposes the controller; afterwards the new root mounts lazily.
- Stable `key`: Woge keeps the **original node, subtree, props and controller**. Incoming placeholder
  props are ignored, not silently reconciled. A key denotes the same widget/type/schema. No nested
  islands or registered Woge regions inside an island are allowed.
- Reinitialize/remove a keyed island deliberately: set `data("woge-state", "reset")` on its stable
  containing region before Replace. A collection Remove also disposes the island. Missing or
  incompatible keyed placeholders otherwise reject the patch; changing the key is not reset.
- Local application code can explicitly call
  `installWogeIslands().update(root, { maxLength: "150" })`. This is an update of public string props,
  not a server reconciliation protocol. Unrelated regions remain independent.
- Before lifecycle events are not commit promises. If a later browser-state check rejects a patch,
  a disposed unkeyed controller is not rolled back. Prefer stable keys for browser-owned widgets.
- Woge disposes before patch removal and cancels pending mounts, and disposes on real page unload
  (but keeps BFCache state). For DOM changes outside Woge events, explicitly dispose the manager and
  reinstall or call `scan()` for newly added roots.

See [browser-owned state](browser-owned-state.md) and [browser lifecycle](browser-replace-runtime.md).

## Serve it, without requiring Node

The JVM `woge-fallback-client-assets` artifact serves `/assets/woge/islands.js` alongside existing
assets for Spring MVC, WebFlux and Ktor. Put your ordinary JS module in your application's static
assets. Plain HTML/CSS/JS applications need no Node build. The reference additionally builds its opt-in shared Kotlin rules,
as described below.

For npm deployment, copy the complete `dist` output; expose its `islands.js` at the loader path, or
load it using your own external module script. Bundlers can import `installWogeIslands` from
`@woge/fallback-client/islands`. Loading the module auto-installs after DOMContentLoaded; explicit
installation is idempotent. CSP must allow your same-origin modules (`script-src 'self'`, or nonce
policy with trusted module dependencies). There is no eval or inline executable bootstrap.

## Small local reactions and optimistic feedback

The separate [signals and optimistic actions guide](island-signals.md) shows `signal`, lazy
`computed`, disposable `effect`, synchronous `batch` and explicit authoritative reconciliation.
Use them only when normal server forms/patches are insufficient. Nothing renders or diffs DOM, and
an optimistic counter is never proof of a successful save. The module is opt-in, not part of core.

## Kotlin/JS authoring

Use a **separate opt-in Kotlin Multiplatform/JS IR module** with `js(IR)`, `useEsModules()`, a browser
target and production DCE/minification. Export an entry with `@JsExport` (or a small JS wrapper) whose
actual named ES export implements `mount(root, props, context)` and returns synchronous `update` /
`dispose` functions. Use `kotlinx.browser` DOM bindings where helpful. Decode the public versioned
string-field props into a browser-only type; do not share server persistence/authentication types.
Share only pure common validation/state logic if needed. Keep DOM/server adapters separate.

Deploy the production module and any chunks at your registered same-origin path. Inspect its actual
ES export name, retain it during minification, and bump `assetVersion`. Measure all app-owned chunks,
not just the entry. Framework budgets exclude application islands: Kotlin stdlib/serialization can
be substantial for a trivial counter. No framework Gradle Kotlin/JS asset integration is provided; the reference has an example-owned copy task. [ADR 0093](../adr/0093-kotlin-js-local-islands.md) explains the target and
[ADR 0094](../adr/0094-lazy-local-island-lifecycle.md) the lifecycle/trust decisions.

## Sharing rules: a practical note draft, not another application framework

If you mostly write HTML and JavaScript, think of `commonMain` as a small library compiled twice:
ordinary JVM code for the server, and JavaScript for the browser. Kotlin Multiplatform does **not**
share the page, database or DOM. The [note rules](../../examples/reference-application/shared-rules/src/commonMain/kotlin/dev/woge/example/rules/NoteDraft.kt)
share only required/length validation, safe error messages and a draft state machine:
`DRAFT → VALIDATING → READY` or `REJECTED`. Editing starts a fresh draft. Only a confirmed server save
moves its server-owned draft to `SUBMITTED`; local `READY` never means saved or authorized. The island
leaves the real form POST alone. Optimistic action reconciliation is not implemented here (#56).

The [JavaScript adapter](../../examples/reference-application/shared/src/main/resources/static/assets/note-counter.js)
imports the generated `reviewNote` ES export. It does not copy Kotlin rules into JavaScript. Every input
runs the shared transitions immediately, updates `aria-invalid`, and writes errors using `textContent`
in the existing status element. The [server action](../../examples/reference-application/shared/src/main/kotlin/dev/woge/example/notes/Notes.kt)
creates its **own** draft from decoded text and runs the same validation before any persistence.
Client state, props and successful local validation are untrusted. A direct or modified POST still
receives HTTP 400 with the same `MISSING` / `MALFORMED` code and message. The existing `FormErrors` and
validation-patch helpers produce a focusable summary for native and enhanced responses (ADR 0058).
Server errors live outside the keyed island; its original input and local controller survive patches.

This is deliberately a public, in-memory demo, not private notes. Host same-origin/CSRF verification
and the action's explicit CSRF check stay server-only. A real private app must authorize the current
principal and resource **on every request** before validation rendering and saving; add transaction,
rate-limit and concurrency policy there too. No authorization, trusted pricing, secrets, data access
or server-state decision belongs in these shared sources. Never ship server credentials or accept a
client `SUBMITTED` phase as proof of persistence.

### Wire and Unicode rules

Props are exactly version 1 and `{ "maxLength": "120" }`; extra keys, noncanonical numbers and other
versions fail before mounting. The shared string-field draft codec accepts only `{ "text": "…" }`,
preserves text literally, and always restores `DRAFT`, never a serialized phase. Woge already safely
encodes the props JSON; normal form decoding handles URL-encoded text on the server. No extra JSON
or RPC dependency is needed. Common tests exercise quotes, newlines, HTML characters and emoji;
server tests additionally exercise the actual form and existing JSON command serializer.

Length is **UTF-16 code units**, matching HTML `maxlength`: an emoji surrogate pair counts as two,
not one grapheme. Validation uses an explicit Unicode whitespace set, including NBSP and BOM, so JVM
and JS do not disagree about a blank note. It does not normalize text. Error codes reuse the host's
vocabulary: blank is `MISSING`, excessive length is `MALFORMED`; length detail is the safe message.
Native required validation may differ for whitespace, so the server still checks it.

### Build and test this example

The [example build](../../examples/reference-application/shared-rules/build.gradle.kts) selects JVM
and Kotlin/JS IR ES modules. Pure `commonTest` tests run unchanged with JVM and **Node**, not Karma:
Node executes the browser-target JavaScript logic without a DOM or browser download. Actual Chromium
note journeys prove the compiled module loads and runs under CSP, native HTML works without JS,
failed imports retain useful HTML, and bypassed local rules receive matching server errors.
The same journeys run for Spring WebFlux, Spring MVC and Ktor in `referenceBrowserSmoke`.

```shell
./gradlew :woge-reference-shared-rules:jvmTest :woge-reference-shared-rules:jsNodeTest :woge-reference-shared:test
WOGE_REFERENCE_PORT=18081 ./gradlew referenceBrowserSmoke
```

Install Node/npm (CI uses Node 24) only when building this example or running the repository gate.
Framework `modules/*` and `adapters/*` remain JVM-only. Root `check` explicitly includes the example's
`check`, so both shared test targets run in CI. Kotlin reuses system Node/npm, does not download Node
or Yarn, and records npm resolution in `kotlin-js-store/package-lock.json` for reproducibility.
Gradle/npm caches and up-to-date checks avoid repeated compilation and installs.

`noteRulesAssets` depends on `jsProductionExecutableCompileSync` and copies **all** production ES
modules into generated reference resources under `/assets/note-rules/`. They are packaged in the
shared JVM jar, served by the existing host wiring; generated JS/maps are not checked into source.
The production compiler performs DCE and shortened member naming without a webpack/Karma build.
The wrapper's registry asset version is bumped when its deployment changes. Deploy the wrapper and
all imported modules together, with revalidation/no immutable caching of unversioned child chunks.
Automatic hashed asset registration and general application tooling remain #242, not framework work
hidden in this example. See [ADR 0095](../adr/0095-shared-example-rules-build.md).

### When is sharing worth it?

Share **pure, deterministic, frequently reused rules** when immediate feedback avoids mistakes and
matching error codes/messages matter. A meaningful draft workflow with several transitions can
justify the extra Kotlin runtime download and build complexity. Keep the library small and measure
all module bytes, not only the wrapper. For a counter, one trivial check, rapidly changing UI copy,
or a JavaScript-only team, ordinary JavaScript plus authoritative server validation is often simpler.
Do not share authorization, pricing, database lookups, secrets, or rules needing current server state.
Those must run on the server regardless of how attractive a client preview looks.

## Diagnostics

Listen for bubbling, composed `woge:island-error`. Its frozen `event.detail.code` contains no props,
URL, exception text or HTML. These failures do not disable the containing native form:

| Code | Meaning |
| --- | --- |
| `WOGE_ISLAND_MANIFEST_INVALID` | Missing, duplicate or invalid initial allowlist |
| `WOGE_ISLAND_UNKNOWN_TYPE` | Type is absent from the page manifest |
| `WOGE_ISLAND_PROPS_INVALID` | Wrong version, field shape, props template or loading strategy |
| `WOGE_ISLAND_OWNERSHIP_INVALID` | Nested island or registered region inside an island |
| `WOGE_ISLAND_IMPORT_FAILED` | Module download/evaluation failed |
| `WOGE_ISLAND_MOUNT_FAILED` | Missing export or synchronous mount/controller failure |
| `WOGE_ISLAND_UPDATE_FAILED` | Missing update controller, invalid props or update error |
| `WOGE_ISLAND_DISPOSE_FAILED` | Application cleanup threw; signal is still aborted |

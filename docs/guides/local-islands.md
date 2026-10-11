# Local islands: one widget, not a hydrated page

An island adds local behavior to **one** useful server-rendered root. Your links, form, validation and
other page content still work before JavaScript loads or when a module fails. For example, the
[reference notes form](../../examples/reference-application/shared/src/main/kotlin/dev/woge/example/notes/NoteIsland.kt)
shows native required/maxlength validation and a server hint; a lazily loaded
[plain JavaScript counter](../../examples/reference-application/shared/src/main/resources/static/assets/note-counter.js)
adds immediate character feedback. Server validation remains authoritative.

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
assets. The reference uses precisely this path. Plain HTML/CSS/JS applications need no Node build.

For npm deployment, copy the complete `dist` output; expose its `islands.js` at the loader path, or
load it using your own external module script. Bundlers can import `installWogeIslands` from
`@woge/fallback-client/islands`. Loading the module auto-installs after DOMContentLoaded; explicit
installation is idempotent. CSP must allow your same-origin modules (`script-src 'self'`, or nonce
policy with trusted module dependencies). There is no eval or inline executable bootstrap.

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
be substantial for a trivial counter. No root Gradle Kotlin/JS integration is required or provided
in this first boundary. [ADR 0093](../adr/0093-kotlin-js-local-islands.md) explains the target and
[ADR 0094](../adr/0094-lazy-local-island-lifecycle.md) the lifecycle/trust decisions.

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

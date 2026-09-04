# Apply a patch stream in the browser

Woge pages are useful server-rendered HTML first. The fallback runtime is a small enhancement: it can
apply a server response to a known region without taking ownership of the whole page.

## Mark the active page and regions

The page head carries one opaque page epoch. Each patchable element carries an opaque region ID and a
revision:

```html
<head>
  <meta name="woge-page-epoch" content="epoch-a">
</head>
<body>
  <main class="project-summary" data-woge-region="summary-1" data-woge-revision="0">
    <p>This normal HTML is the no-JavaScript fallback.</p>
  </main>
</body>
```

The ID is not a CSS selector. Woge scans the fixed `data-woge-region` attribute once and stores each
element in the active page registry. A patch supplies only the opaque map key.

## Apply a Fetch response

```js
import { createWogePatchRuntime } from "@woge/fallback-client";

const runtime = createWogePatchRuntime(document);
const response = await fetch("/projects/42/summary");

if (!response.body) throw new Error("Expected a streamed response body");
const completion = await runtime.applyPatchStream(response.body);
console.log(`Applied ${completion.patchCount} patches`);
```

Bundled TypeScript declarations describe this API and the lifecycle-event details for IDEs and code
generation tools. The runtime itself stays plain JavaScript and uses only browser standards.

The runtime reads ordinary `Uint8Array` chunks from the Fetch `ReadableStream`. Network chunks are not
frames: a proxy may split one frame into many chunks or combine many frames in one chunk. Woge uses
the protocol lengths and verifies the terminal frame before reporting completion.

Create one runtime for one active document. A full navigation creates a new page epoch and therefore a
new runtime. Fetch/form interception is deliberately deferred; normal links and forms remain the
baseline until the action enhancer installs this call.

## Keep normal CSS and custom elements

Replace keeps the region element and replaces only its children. Classes, inline standards CSS,
Tailwind utilities, data/ARIA attributes and a custom-element region therefore remain application
owned. Valid custom elements in the new content connect through the browser's normal lifecycle.

Nested Woge regions are allowed. Their opaque IDs and counters are validated before the parent DOM is
changed, then added to the same page registry atomically.

## Delegate controller lifecycle

The target emits standard bubbling events immediately before and after replacement:

```js
document.addEventListener("woge:before-replace", (event) => {
  disposeControllersInside(event.target);
});

document.addEventListener("woge:after-replace", (event) => {
  mountControllersInside(event.target);
  updateControllerOn(event.target, event.detail);
});
```

This is ordinary event delegation: one listener can serve the page, including content inserted later.
There is no Woge hydration tree. Open dialog and popover descendants are closed between these events
before removal; an overlay that is itself the stable region target is retained.

Focus, selection and dirty input preservation require more policy than these lifecycle events and are
implemented separately in issue #36.

## Handle failures without best effort

```js
import { WogePatchError, WogeRemotePatchError } from "@woge/fallback-client";

try {
  await runtime.applyPatchStream(response.body, { signal: abortController.signal });
} catch (problem) {
  if (problem instanceof WogeRemotePatchError && problem.recovery === "reload") {
    location.reload();
  } else if (problem instanceof WogePatchError) {
    console.warn(problem.code);
  }
}
```

The runtime rejects unknown/duplicate targets, stale epochs, interaction/revision mismatches,
malformed framing and non-canonical metadata. It also rejects scripts, inline `on*` handlers,
embedded documents and dangerous URL schemes before changing the current region. It never tries to
repair or partly apply an invalid Replace patch.

A valid earlier frame may already be visible when a later frame fails. That is intentional streaming
between atomic patches, not one transaction for the complete response.

## What comes from Kotlin?

Application Kotlin code creates typed `ReplacePatch` values; the shared server encoder turns them into
the byte stream. The browser API above does not require Kotlin knowledge. Its JVM/browser Golden
fixture proves both sides agree on field names, lengths, UTF-8 and 64-bit revision values.

See [the patch-stream codec guide](patch-stream-codec.md) for the server side and
[ADR 0025](../adr/0025-browser-replace-runtime-and-lifecycle.md) for the complete ownership decision.

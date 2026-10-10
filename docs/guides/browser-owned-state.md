# Preserve browser-owned state

Keep visible form controls outside frequently replaced regions when possible. The
[task board](../../examples/reference-application/README.md#try-a-multi-region-action) does this with
ordinary HTML `form` attributes. When a region must contain controls, give each a stable state key:

```kotlin
input(attributes = {
    attribute("name", "title")
    data("woge-state-key", "task-title")
    attribute("value", task.title)
})
```

Use the same key for the same control in the replacement. Keys are unique within the replaced
subtree; they are not CSS classes, selectors, authorization or business IDs.
The typed `formField(field, errors)` helper supplies the state key from its explicit field ID,
so component fields and native/enhanced validation use the same ownership rule.

| State | Owner and replacement rule |
| --- | --- |
| Clean input value | Server supplies the next value |
| Dirty text/value input or textarea | Browser value survives with a compatible stable key |
| Dirty checkbox/radio | Browser checked state survives with a compatible stable key |
| Dirty select | Browser selections survive if all selected values still exist |
| Hidden inputs | Server updates them, including page/version/revision fields |
| Active keyed element | Focus returns to its match without scrolling; supported text selection returns too |
| Active unkeyed element | Native removal behavior; use a key or keep it outside the region |
| File input | Same native node survives; never copy or assign file values |
| Contenteditable | Keep as an explicit local island or explicitly reset; no generic range reconstruction |
| Custom element/local island | Matching keyed `data-woge-island` root retains its existing subtree |
| Dialog/popover | Stable region root stays; removed descendants close; no automatic reopening |
| Media playback/time and scroll | Not reconstructed; keep native elements outside replacement or use explicit controller lifecycle |

A dirty control without a compatible match rejects the patch before DOM replacement rather than
silently losing edits. A keyed focused element without a match needs a focusable region fallback,
usually `tabindex="-1"`. Selection restoration is supported for textarea and text/search/url/tel/password
inputs; email, number and other native inputs retain values, not text selection APIs.

To deliberately accept a server reset, put `data-woge-state="reset"` on the incoming control.
To discard all local state in a region, put it on the stable region root. A whole-region reset
allows native focus loss; validation then explicitly focuses its document-owned error summary.
Neither reset nor state conflict automatically resubmits a form.

For a local editor:

```kotlin
div(attributes = {
    data("woge-state-key", "editor")
    data("woge-island", "")
}) {
    div(attributes = { attribute("contenteditable", "true") }) { text(initialText) }
}
```

Both old and incoming island roots need the marker and the same key. Islands cannot overlap or
contain registered Woge regions. Native nodes are moved, not cloned; controller lifecycle and
disconnect/reconnect behavior remain ordinary browser behavior. This does not promise uninterrupted
media playback or open overlays.

Tools can reuse `captureWogeBrowserState(root)` and
`prepareWogeBrowserState(snapshot, detachedDestination, { reset: false })`. Preparation validates
and restores detached values. Call its `commit()` once immediately before replacing content,
then `restoreFocus(stableRegion)` afterward. These are in-memory DOM primitives, not a persistence
or full-refresh API. Never log or store snapshots: they can contain sensitive inputs.

## Development full reloads

The development client can preserve focus, supported text selection and document scroll around
its own successful-build reload. To also preserve a **non-sensitive** dirty control, give it a
stable state key (or native `id`) and explicit storage permission:

```kotlin
input(attributes = {
    attribute("name", "draft")
    data("woge-state-key", "demo-draft")
    data("woge-development-preserve", "")
})
```

Only opt in disposable development data. `sessionStorage` is readable by other scripts on the same
origin; do not opt in real personal information, tokens or secrets. Password, file and hidden
inputs are excluded even with this marker, as are sensitive autofill fields and `autocomplete="off"`.
Dirty text, checked state and selected options share the production ownership rules; server
defaults remain unchanged. An incoming control or ancestor with `data-woge-state="reset"` accepts
the server state. A body reset also discards scroll.

The handoff is tab-local, expires after one minute and is consumed once. It applies only to the
same URL, build, generation and development session, not a navigation to another page. Storage or
matching failures emit a generic console warning and keep the ordinary refreshed document; they
never trigger another reload or resubmit a form.

Full reloads cannot retain native file nodes, local islands, contenteditable, open dialogs/popovers
or media playback. These keep ordinary native reload behavior. No file value is reconstructed.
The production region-replacement API and its in-memory snapshots remain separate.

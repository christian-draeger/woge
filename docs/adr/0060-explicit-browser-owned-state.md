# ADR 0060: Preserve browser-owned form state with stable keys

- Status: Accepted
- Date: 2026-10-10
- Decision owners: Woge maintainers
- Related issues: [#36](https://github.com/christian-draeger/woge/issues/36), [#150](https://github.com/christian-draeger/woge/issues/150)
- Refines: [ADR 0025](0025-browser-replace-runtime-and-lifecycle.md), [ADR 0059](0059-identified-collection-patches.md)

## Context

A server response can update a region while a user is typing. Replacing its children must not
silently discard an unsaved value. CSS classes and input positions are not stable identities.
File selection belongs to a native input, not to a value that JavaScript can reconstruct.

## Decision

Use an explicit `data-woge-state-key` on controls that may be replaced while interacting.
Keys are opaque ASCII IDs, unique inside one replacement scope. No selector supplied by the
server is evaluated. Existing region IDs and revisions keep their separate meaning.

Replace captures browser state, validates matching keys and compatible control types, then prepares
the detached replacement. Clean controls accept server values. Dirty values, checked state and
selected options keep the user's state. Missing keys, incompatible controls and missing dirty
options reject the patch before Woge replaces content. Dirty means different from the native
default, including programmatic edits; hidden ordering fields remain server-owned.

The active keyed control receives focus after replacement, with `preventScroll`. Supported text
controls also retain their selection range and direction, clamped to the new value. A deliberately
moved external focus is not stolen back. A missing keyed focus target uses the stable region,
which the application must make focusable. Unkeyed focus follows native removal behavior; use a
key or keep visible controls outside replaced regions.

An incoming control can declare `data-woge-state="reset"` to accept its server value deliberately.
The stable region can declare that attribute to reset its whole subtree, discarding browser state
and allowing native focus loss. This is explicit application policy, not an automatic reaction to
a conflict. Validation still owns its final summary focus through ADR 0058.

File inputs keep the same native node through matching keys. Woge never assigns a FileList or file
value. A local island can likewise declare a key and `data-woge-island` on its matching root.
The old subtree replaces the new placeholder, retaining local content and controller state.
Islands cannot overlap or contain Woge region roots. Application controllers can instead use the
ordinary before/after lifecycle events; they do not own the surrounding Woge region.

Expose opaque in-memory `captureWogeBrowserState` and `prepareWogeBrowserState` primitives. Production
Replace uses these same functions. A future development refresh can reuse the ownership contract
without making production patches perform full-document refresh or persistent storage. Snapshots
may retain sensitive input; do not serialize, persist or log them.

## Alternatives considered

- **Match by CSS classes or control position:** rejected; styling and ordering are not identity.
- **Overwrite every value:** rejected; loses unrelated user edits.
- **Preserve every value forever:** rejected; clean values and hidden server fields must update.
- **Copy selected files:** rejected; violates native input ownership.
- **Automatically replay a failed mutation:** rejected; state preservation grants no retry permission.

## Consequences

Application markup must supply stable keys where replacing interactive content is intentional.
Keeping visible controls outside replaced regions remains simpler when possible, as the reference
task board demonstrates. Plain HTML forms and native validation still work without JavaScript.

Contenteditable is supported only as an explicit local island or deliberate reset. Media playback,
playback time, document scroll, arbitrary contenteditable selection and open overlay restoration
are not reconstructed. Stable overlay region roots remain intact; removed overlays retain the
existing close policy. An island retains its subtree, but the native disconnect/reconnect behavior
of media and overlays is not a guarantee of uninterrupted playback or open state.

Failures use stable browser-apply codes and fail closed. A stream is still not atomic; focus
failure after insertion cannot promise rollback of already applied content.

## Follow-up

Browser tests cover keyboard focus, text selection, dirty values, checkboxes/radios, multi-select,
native files, explicit reset, islands, failed matches, stable dialogs/popovers, combobox controls and
table edits during unrelated patches. Development full-refresh persistence remains #150; it must
reuse this ownership model without silently changing production Replace behavior.

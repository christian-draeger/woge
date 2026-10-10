# ADR 0061: Preserve explicitly opted-in state around development reloads

- Status: Accepted
- Date: 2026-10-10
- Decision owners: Woge maintainers
- Related issues: [#150](https://github.com/christian-draeger/woge/issues/150)
- Builds on: [ADR 0044](0044-development-browser-snapshots-and-explicit-opt-in.md), [ADR 0060](0060-explicit-browser-owned-state.md)

## Context

A successful development build currently reloads the document. Unlike a region replacement, a
reload cannot retain DOM nodes or an in-memory snapshot. Unsaved demo input, keyboard focus and
scroll should survive when the new document still represents the same page and controls.
Persisting arbitrary form values would also persist secrets, so ownership alone is not permission
to store a value.

## Decision

Keep the full-reload baseline. Just before Woge initiates it, capture one bounded handoff in
tab-local `sessionStorage`. Restore only on a reload of the exact same URL, matching rendered
build, server generation and development session. Consume the entry before validation. Entries
expire after one minute; storage is limited to 64 KiB, 200 dirty controls and 4096 characters per
value. Storage failure, invalid data or incompatible controls produce a generic console warning
without values, then leave the ordinary refreshed document usable. Never reload to retry restoration.

Values need a stable `data-woge-state-key` or native `id`, plus explicit
`data-woge-development-preserve` on the control. This is additional permission to store a
non-sensitive development value, not a production form feature. Never store hidden fields,
passwords, files, payment/password/one-time-code autofill controls or controls with autocomplete
disabled. Do not opt in real personal information, tokens or other secrets in ordinary text fields.

Production Replace and development reload share the control-value, dirty-default, compatibility,
selection and value-write helpers. Dirty values update live properties, never HTML defaults.
Clean controls still receive the server's new default. `data-woge-state="reset"` on a control or
ancestor discards that state explicitly. All eligible dirty matches are validated before any value
is written; an incompatible match abandons the value handoff rather than restoring by position.

Restore focus by semantic key or native ID, not CSS styling selectors. Restore supported text
selection and clamp it to the new value. Do not steal a focus already owned by autofocus or
application code. Restore document scroll without smooth animation. Reset boundaries also prevent
focus recovery; a body reset prevents scroll recovery.

Each tab owns its storage and SSE connection. An entry copied when opening another tab cannot
apply on ordinary navigation. Wrong URL, build, generation, session or age consumes the entry
without restoration. A failed build never captures or refreshes.

## Alternatives considered

- **Persist all keyed values:** rejected; a replacement key is not consent to persist a secret.
- **Serialize the production snapshot:** rejected; it holds DOM nodes and possibly sensitive values.
- **Guess matches from CSS or position:** rejected; visual similarity is not semantic identity.
- **Introduce a document patch or reverse proxy now:** deferred; keep the correctness baseline first.

## Consequences

The plain development modules are served without Node or bundling. Gradle copies the same small
control helper used by the production client into the development resources; it does not add a
production dependency on development tooling.

Files are never reconstructed or assigned. Dialogs and popovers are not reopened, contenteditable
and local islands are not serialized, and media playback/controller state is not recreated.
For these states, a full reload keeps native reload behavior; region replacement retains its
separate native-node policy from ADR 0060.

`sessionStorage` is accessible to scripts on the same application origin. The explicit opt-in is
only for non-sensitive development data. Values never enter URLs, SSE messages or server logs.

## Follow-up

Browser evidence covers successful and failed builds, independent tabs, focus/selection/scroll,
dirty controls, reset, files and sensitive-value exclusion, unavailable storage and navigation.
Evaluate a Woge-native document refresh only after this baseline proves reliable in sustained
development sessions; it must not silently change production Replace ownership.

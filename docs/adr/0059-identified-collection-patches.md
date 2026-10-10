# ADR 0059: Append and remove identified items inside known regions

- Status: Accepted
- Date: 2026-10-10
- Decision owners: Woge maintainers
- Related issues: [#32](https://github.com/christian-draeger/woge/issues/32), [#33](https://github.com/christian-draeger/woge/issues/33)
- Refines: [ADR 0023](0023-minimal-replace-patch-ir.md), [ADR 0025](0025-browser-replace-runtime-and-lifecycle.md), [ADR 0048](0048-document-owned-accessibility-announcements.md)

## Context

Interactions need small collection updates without replacing every existing row. A generic selector
or DOM diff would hide item identity and make ordering and focus difficult to reason about.

## Decision

Add typed `AppendPatch`, `RemovePatch` and `PatchItemId` to the existing semantic Patch IR.
Every operation targets one registered region in one page epoch and continues its exact revision
and interaction sequence. No operation takes an arbitrary selector.

An append contains one identified direct-child item. `patchItem` renders its single root through
the safe HTML DSL, with `data-woge-item` and normal application attributes. The browser checks its
root identity, active-content policy and nested region IDs before insertion. Item IDs are scoped
to direct children of the target collection, not to the whole document.

If an append ID already exists, retain that item's content and advance the collection revision.
If a removed ID is already absent, likewise advance the revision without changing content.
These are explicit idempotent item operations, not permission to replay stale frames: epoch,
interaction and the exact revision transition are checked first. Duplicate IDs already present
inside a collection fail closed.

A remove has an empty payload and names a registered fallback region for focus. It cannot name
a region inside the removed item. Move focus only if the item contains the current focus; the
fallback must already be focusable, for example a collection with `tabindex="-1"`. If it cannot
receive focus, reject removal rather than silently dropping focus into the document body.
Unfocused removal leaves focus alone. Close removed dialog/popover overlays and unregister nested
regions. This is the necessary focus recovery for removal, not a general focus or announcement op.

Use the existing version-1 framing, adding canonical operation-specific metadata fields. Existing
Replace frames remain byte-for-byte unchanged. Old Replace-only clients reject the new operations;
applications must deploy the updated client before using them. Unsupported operations still fail
through the canonical recovery contract, never through best-effort DOM mutation or POST replay.

The runtime exposes `Flow<Patch>.encodePatchStream` through the same host chunk transport. Deferred
and prepared action replacements reuse that encoder. Typed action collection builders and the
reference application's multi-region workflow remain #33.

## Alternatives considered

- **Append arbitrary HTML without identity:** rejected; retries would duplicate rows.
- **Skip all duplicate revisions:** rejected; item deduplication does not relax state ordering.
- **Automatically focus the nearest button:** rejected; the application knows the meaningful fallback.
- **Remove the collection region itself:** rejected for this operation; it would lose the revision
  owner and make subsequent updates ambiguous.
- **Attribute diffs or generic announcements:** rejected; both remain outside the small operation set.

## Consequences

Collections retain existing row state on append, and removal has a predictable, explicit focus path.
All three operations use the same framing, validation and observability code.

Applications must render stable item IDs and a focusable fallback region. Lifecycle events let
application controllers clean up and mount content; listeners must not mutate the target's identity,
revision or item ownership behind Woge's back. Each frame is validated, but a complete stream is
still not one atomic transaction.

## Follow-up

The shared JVM/browser golden covers Replace, Append and Remove. Browser conformance covers
deduplication, revisions, removal focus, nested target cleanup and invalid item payloads. Full
interaction state preservation remains #36; typed multi-region collection composition remains #33.

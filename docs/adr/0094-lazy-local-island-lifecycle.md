# ADR 0094: Load root-local islands through an immutable page manifest

- Status: Accepted
- Date: 2026-10-11
- Decision owners: Woge maintainers
- Related issues: [#54](https://github.com/christian-draeger/woge/issues/54)
- Refines: [ADR 0093](0093-kotlin-js-local-islands.md), [ADR 0060](0060-explicit-browser-owned-state.md)

## Context

Useful server HTML sometimes needs a small, continuous local interaction. It must not turn into
whole-page hydration or allow a server patch to choose arbitrary executable assets. The core browser
bundle is already 35,968 of 36,000 raw bytes. JVM HTML/CSS applications must remain Node-free.

## Decision

`IslandType<Props>` names a stable type, positive props schema version and explicit projection from a
Kotlin value to `IslandProps`. Core has no serialization dependency: the initial boundary deliberately
supports JSON objects of string fields only. Applications parse domain scalars explicitly in their
module; nested/shared DTO serialization can be a later, separately reviewed extension. Only public,
selected data belongs here, never server models, authorization decisions or secrets. JSON strings
escape controls, surrogates, HTML delimiters and line separators; the browser validates the version
and string-field shape without evaluating code. The application validates field meanings.

`IslandRegistry(IslandModule(...))` generates an inert head manifest from typed declarations, once per
page. Each type maps to a same-origin path-only `.js`/`.mjs` module, an explicit asset-version query and
mount export. No protocols, network-path URLs, encodings, traversal, arbitrary queries or fragments are
accepted. The initial-document allowlist is copied and never re-read from patched content. A patch
carries only the root's type and props; unknown types fail closed. Asset versions must change whenever
the deployed module changes. No build-time Gradle/Node integration is needed for plain ES modules.

`island` writes a `div` with useful fallback HTML and a root-local `template[data-woge-island-props]`
containing escaped JSON text. A template is deliberately used instead of a root-local JSON script:
it is equally inert, invisible and root-scoped, and existing server/browser patch policies continue
rejecting **all scripts**. We do not expand active-content policy or core bytes for this feature.
`islandManifest` uses `script[type=application/json]` only in the original head. `islandLoader` emits
one external module script on opted-in pages; the separately budgeted `islands.js` discovers roots.

Lifecycle and ownership:

- `mount(root, props, {signal, propsVersion})` is synchronous and returns an optional
  `{update(props), dispose()}`. Only the chosen root is owned; islands cannot nest or contain a
  registered Woge region. There is no client renderer, page store or hydration graph.
- `visible` (default) waits for IntersectionObserver; `eager` mounts immediately. Without that
  optional browser API, visible falls back to eager. Each module URL is imported at most once per
  manager; each root mounts at most once. Failed imports are not automatically retried.
- `installWogeIslands().update(root, stringFields)` explicitly calls an existing controller's update.
  Props on a preserved incoming placeholder do **not** overwrite local state. The key denotes the
  complete original root identity, including type, schema, HTML and props. Keep them compatible or
  deliberately reinitialize. An update is application policy, not implicit server reconciliation.
- ADR 0060's `data-woge-state-key` retains the original subtree/controller across a containing
  Replace. A missing/incompatible keyed placeholder rejects replacement through existing state
  validation. To reinitialize or remove a keyed island, set `data-woge-state="reset"` on the stable
  containing region (or use collection Remove); changing/removing the key alone is not a reset.
- Delegated before/after Replace and collection Remove/Append events dispose unkeyed/reset roots
  before disconnection and discover new roots afterwards. Keyed preserved roots are not disposed.
  This follows ADR 0025's existing seam: a before-event can run even if a later browser-state check
  rejects the patch; disposed unkeyed controllers are not promised rollback. Keep browser-owned
  widgets keyed, and avoid relying on side effects in other before-event listeners.
- Disposal aborts the root's signal, calls its controller once, stops observation and prevents a
  pending import from mounting a removed root. Real pagehide disposes the manager; BFCache pagehide
  retains it. Manual DOM changes outside Woge patches require explicit manager disposal/scan.
- Import/unknown type/invalid props failures leave fallback untouched. A synchronous mount failure
  aborts signal-managed resources and restores cloned fallback children. Applications must use the
  signal for listeners and asynchronous work and mount synchronously; arbitrary application side
  effects, file selection and asynchronous mutations cannot be magically rolled back.

Report constrained codes through the bubbling, composed `woge:island-error` DOM diagnostic event;
never echo props, module contents or caught exceptions. An optional island error does not mark an
otherwise valid patch failed or interfere with native form submission/server validation. CSP permits
only trusted external module sources; no eval or inline executable bootstrap is required. See the
[public guide](../guides/local-islands.md) for codes and deployment instructions.

The supported Kotlin target remains Kotlin/JS IR ES modules with production DCE/minification
(ADR 0093). The reference note counter is a plain JS module, so no Kotlin/JS/Node toolchain enters the
root JVM gate. Framework loader bytes have independent raw/gzip/Brotli ceilings; application-owned
islands report their own cost outside framework budgets (ADR 0083).

## Alternatives considered

- **Dynamic module URL attributes in patches:** rejected; executable imports must come from the
  immutable page allowlist, not mutable region content.
- **Extend core with an island framework:** rejected; standard lifecycle events already suffice and
  the current core ceiling has almost no space.
- **Root-local JSON scripts in patches:** rejected for now; templates meet the same inert boundary
  without weakening the existing script ban or changing adapters/patch protocol.
- **Automatic update from preserved placeholders:** rejected; it would silently merge server props
  into explicitly browser-owned state and discard ADR 0060's exact-node contract.
- **Require kotlinx.serialization in core or a Kotlin/JS root build:** rejected for this minimal
  boundary; applications may opt into Kotlin tooling without making plain HTML users pay for it.

## Consequences

The framework adds one optional dependency-free loader and a small typed Kotlin API. Existing
adapters, patch bytes and core bundle remain unchanged. The same API works for MVC, WebFlux and Ktor.
String-field props are intentionally less expressive than a general JSON serializer; applications
validate their semantics. Before-event disposal inherits the existing lifecycle seam's non-rollback
limitations. Local widgets own cleanup and accessibility while server HTML remains authoritative.

## Follow-up

- Extend props to a typed nested JSON/serialization bridge only with bounded validation and tests;
  do not introduce server-only DTOs into browser builds. Shared pure validation remains #55.
- Offer opt-in Gradle Kotlin/JS IR production asset registration: derive versioned manifest entries,
  preserve ES mount exports, report application module bytes and never require Node for JVM-only apps.
- Consider a post-preflight/pre-disconnection lifecycle seam for rollback-safe controller disposal,
  independently budgeted against existing core ceilings; current before events are not commit promises.
- Local signals and explicit optimistic reconciliation remain #56; no implicit props/state merging.

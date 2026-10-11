# ADR 0092: Apply bounded patch transactions independently of animation

- Status: Accepted
- Date: 2026-10-11
- Decision owners: Woge maintainers
- Related issues: [#52](https://github.com/christian-draeger/woge/issues/52)
- Refines: [ADR 0057](0057-prepared-typed-action-region-updates.md), [ADR 0060](0060-explicit-browser-owned-state.md)

## Context

Prepared action updates render every region before responding, but separate patch frames can become
visible separately. Updating a total and its list needs one declared visual unit. Animation cannot
be necessary for correct HTML, focus, ordering or recovery. The core's fixed raw-byte ceiling is
36,000 bytes; document navigation already demonstrates separately budgeted optional capabilities.

## Decision

Expose `PageResult.RegionUpdates.atomic(viewTransition = false)`. It groups **all** prepared typed
replacements, including render-session updates, without rerendering or changing native HTTP behavior.
MVC, WebFlux and Ktor reuse the shared action encoder. This does not transact application data or
permit POST replay. Append/remove and deferred/live streams remain separate operations; applications
can use authoritative replacements when several regions must change together.

Add semantic `PatchTransaction` and version-1 binary frame kind **4**. Its canonical content type is
`application/json; charset=utf-8`; its exact metadata is `{"viewTransition":false}` or
`{"viewTransition":true}`. The binary payload is a complete ordinary version-1 stream containing
1–128 distinct Replace members and one Complete, with one epoch and interaction. Each member owns
its normal patch ID, target and contiguous revision. Nesting, remote Error and non-Replace members
are rejected. Completion counts outer protocol units: a transaction counts once. Prepared builders
also retain their existing member-count limits. The entire inner stream, including framing, fits
one existing 8 MiB payload ceiling. Encoding checks that bound while accumulating, before writing
any outer transaction bytes. Total response-byte limits include the envelope overhead.

Existing frame kinds and golden fixtures are unchanged. Older clients reject kind 4: deploy matching
assets before emitting transactions. The outer browser decoder buffers the complete unit, and the
lazy module validates every member before application. The transaction module is loaded **only** for
a transaction frame, not for ordinary actions, live regions or navigation. Classpath assets ship it
beside `index.js`; npm static deployment copies the entire `dist` directory. Bundlers may split the
dynamic import into a separate chunk. Missing module delivery rejects through the existing recovery
path rather than applying some members. No Node toolchain is required for JVM asset delivery.

Reuse the ordinary replacement preparation and browser-state primitives. Member targets must be
disjoint: no ancestor/descendant targets in one unit. Reject cross-member duplicate incoming region
IDs; existing per-patch registry rules also apply. No region moves between transaction members are
inferred. All before lifecycle events run before browser-state validation; all checks finish before
any member commits. Registry/target integrity is also rechecked between commits and after lifecycle delivery, so
synchronous custom-element/controller interference cannot silently detach a later target.
Commits run synchronously in one task, followed by focus restoration and all
after events. After listeners therefore see the complete new HTML. Listeners still cannot change
region identity/revision or item ownership outside the registry contract.

If any member is stale, ignore the complete transaction; never apply only its current members.
Count it as one stale protocol unit. Recheck current intent inside the actual update callback, so a
new interaction while the module loads or the browser schedules the callback remains authoritative.
Cancellation before that callback also prevents application. One-region GET recovery still accepts
exactly one ordinary Replace, not a transaction or a relaxation of its scope.

Validation failures apply no Woge-owned member mutations and report `WOGE_TRANSACTION_REJECTED`
through the existing browser-apply/fail-closed path. Native DOM/custom-element/focus failures during
commit report `WOGE_TRANSACTION_APPLY_FAILED`, never successful completion. Such exceptional failure
can leave committed HTML; recover with authoritative full navigation, not an inferred rollback or
POST replay. External listener mutations are not a rollback promise. A whole response containing
multiple transactions or ordinary patches is still not atomic.

View Transitions are per-transaction, default off. Call `document.startViewTransition` only when it
exists and `matchMedia('(prefers-reduced-motion: reduce)')` does not match. Wrap the same synchronous
validation/commit callback used by the direct path. Await only `updateCallbackDone` to establish DOM
completion; handle `ready`/`finished` rejection without awaiting animation. API failure before the
callback falls back to direct application once; callback failure remains an application failure and
is never replayed. Unsupported/reduced-motion/opt-out paths have identical final DOM and revisions.

Focus follows existing ownership, not headings, animation or transaction order. Keyed focused
controls keep dirty state and selection; missing keyed focus uses the stable focusable region.
File nodes/islands retain native identity. Reset and unkeyed controls keep their existing policy.
Only the initially active member restores focus, after all commits; external focus or deliberately
moved lifecycle focus is not stolen. Validation summary focus still belongs to the action client.

## Alternatives considered

- **Mark several ordinary frames as a group:** transport splitting could expose partial application.
- **Clone the document or roll back arbitrary DOM/controller effects:** loses native file/island
  identity and cannot undo application side effects. Prepare first and report exceptional failure.
- **Bundle animation and transactions into core or raise ceilings:** violates the optional-cost model.
- **Make animation completion a success condition:** skipped/failed animation cannot invalidate HTML.

## Consequences

Transactions buffer one bounded payload rather than stream member visibility. The core graph permits
exactly its reviewed dynamic import; the transaction's complete dependency graph is separately gated,
with no framework dependencies or unmeasured external imports. Compact existing diagnostics preserve
stable codes and information while making room for this small dispatch/preparation hook. No existing
ceiling is raised: core measures 35,968 raw / 12,146 gzip / 10,764 Brotli bytes. The optional module
and complete graph measure 8,853 / 3,595 / 3,145, below new 10,000 / 4,500 / 4,000 ceilings.

## Follow-up

Retain the shared JVM/browser transaction golden and tests for combined limits, invalid later targets,
state conflicts, lifecycle ordering, stale units, Chromium focus/selection, reduced motion and
unsupported View Transitions. Keep byte inventory and classpath/package delivery verified. Broader
collection transactions or document-epoch migration require a concrete use case and separate design.
See the [typed action guide](../guides/typed-actions.md#atomic-visual-updates).

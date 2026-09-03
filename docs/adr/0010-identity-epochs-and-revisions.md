# ADR 0010: Scope rendered identity and revisions to a page epoch

- Status: Accepted
- Date: 2026-09-03
- Decision owners: Woge maintainers
- Related issues: [#7](https://github.com/christian-draeger/woge/issues/7), [#8](https://github.com/christian-draeger/woge/issues/8), [#19](https://github.com/christian-draeger/woge/issues/19), [#25](https://github.com/christian-draeger/woge/issues/25), [#26](https://github.com/christian-draeger/woge/issues/26), [#32](https://github.com/christian-draeger/woge/issues/32), [#37](https://github.com/christian-draeger/woge/issues/37), [#38](https://github.com/christian-draeger/woge/issues/38), [#41](https://github.com/christian-draeger/woge/issues/41)

## Context

Streaming and concurrent actions can complete out of order. The same component type may appear many times, lists reorder, old tabs survive deployments and live events can be replayed. String DOM IDs or list positions cannot distinguish these cases safely, while a server object retained for every open document would add state, cleanup and clustering costs.

Identity also must not become authorization. A browser can modify every hidden field and DOM attribute it receives.

## Decision

Woge separates component type, rendered instance, region target, page epoch, browser interaction sequence, target revision and append item/event identity. Their exact scope and algorithms are defined in the [identity and revision contract](../architecture/identity-and-revisions.md).

Each complete document has a fresh cryptographically random page epoch carried in an integrity-protected, expiring context token. The token identifies protocol/descriptor-manifest context but contains no authorization decision or sensitive input. It allows stateless verification; every request still authenticates and authorizes its concrete resource.

Rendered component and region IDs are opaque deterministic derivations of the page epoch, generated descriptor, parent/slot path and explicit key where required. Repeated patchable siblings require stable keys; list position is never identity. Raw domain keys are not exposed. Duplicate sibling keys and generated descriptor collisions fail explicitly.

For browser-initiated replacement, the runtime assigns a monotonic interaction sequence when work starts and records it for every affected target. A patch applies only to the active epoch/known target, for the newest registered interaction, from the target's exact current base revision to the next contiguous revision. This makes a later-started search win even when an older request completes last without retaining a server-side page object.

Append additionally requires stable item/event identity and contiguous source ordering. Duplicates are idempotent no-ops; gaps trigger bounded recovery/resynchronization rather than silent skipping. Replace and append therefore have deliberately different conflict rules.

Old/expired epochs, unknown descriptor manifests and incompatible rolling-deployment requests never best-effort dispatch. They return a typed stale/incompatible-page outcome with a canonical full-navigation recovery. Native full-page routes continue to use normal current routing.

Browser sequences and Woge target revisions coordinate presentation only. They do not replace CSRF, domain authorization, database optimistic locking or live-subscription authorization.

## Alternatives considered

- **DOM ID or CSS selector as identity:** rejected because styling/refactors can change it, selectors can over-match and target tampering becomes difficult to constrain.
- **Component type as rendered identity:** rejected because two instances of the same type would collide and required inputs/keys would disappear.
- **List position as an implicit key:** rejected because reorder/insert operations would attach focus, dirty state and patches to the wrong item.
- **Keep one mutable server page/session object per document:** rejected as the default because cleanup, failover and horizontal scaling become part of the core architecture.
- **Accept any higher replace revision:** rejected because a forged/gapped or older-base response could skip unknown state; exact base plus newest-intent checks are deterministic.
- **Use replace ordering for append:** rejected because skipping an append can permanently lose an event even when later state looks newer.
- **Best-effort action dispatch across deployments:** rejected because similarly shaped generated entries can have different semantics after a schema change.
- **Treat opaque/signed IDs as authorization:** rejected because possession by the browser is expected and domain access may change after rendering.

## Consequences

### Positive

- Repeated and reordered components have predictable identities without exposing domain keys.
- Search races, duplicate live delivery and old-page requests have deterministic outcomes.
- Normal operation does not require a retained server object per open page.
- Security checks remain separate and visible.
- Rolling deployments fail toward safe reload instead of accidental dispatch.

### Negative

- Patch metadata includes epoch, interaction and revision context.
- Browser runtime state must track target revisions, active interactions and bounded append gaps.
- Applications must provide stable keys for repeated patchable content.
- Key rotation and descriptor compatibility need an explicit deployment window.
- Some mismatch cases require a region refresh or full navigation instead of applying otherwise valid HTML.

## Follow-up

- Align typed rendered-instance references with this model in [#7](https://github.com/christian-draeger/woge/issues/7).
- Implement canonical key/ID generation and duplicate diagnostics in [#25](https://github.com/christian-draeger/woge/issues/25) and [#26](https://github.com/christian-draeger/woge/issues/26).
- Put epoch, target, base/next revision and operation-specific identity in the Patch IR in [#19](https://github.com/christian-draeger/woge/issues/19) and [#32](https://github.com/christian-draeger/woge/issues/32).
- Turn every required scenario into browser/fuzz fixtures in [#37](https://github.com/christian-draeger/woge/issues/37), [#38](https://github.com/christian-draeger/woge/issues/38) and [#41](https://github.com/christian-draeger/woge/issues/41).
- Select concrete cryptographic primitives, token lifetime and key-rotation configuration during the host/runtime implementation security review.

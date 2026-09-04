# Component identity, page epochs and revisions

This document defines observable identity and ordering behavior. The first implemented protocol values
are `PageEpoch`, `RegionTargetId`, `PatchTarget`, `InteractionSequence`, `TargetRevision` and
`TargetRevisionStep`; generated component/region descriptor names remain follow-up work. Identity
locates a rendered instance; it never grants permission to read or mutate it.

## Identity model

| Value | Scope and source | Purpose |
| --- | --- | --- |
| Component descriptor ID | Generated build manifest; stable while the declaration identity/schema is unchanged | Identifies a component type, not one rendering |
| Page epoch | Fresh cryptographically random value for each complete document/navigation | Prevents an old document or restored request from updating the active document |
| Rendered instance ID | Opaque derivation of epoch, parent instance, slot, descriptor and explicit key where required | Identifies one component instance within one document |
| Region ID | Opaque derivation of rendered instance plus generated region slot | Identifies one legal patch target |
| Interaction sequence | Monotonic browser-local number assigned when an enhanced interaction starts | Expresses the user's latest intent for affected targets; has no authorization meaning |
| Target revision | Monotonic number scoped to page epoch and region | Detects duplicates, gaps and stale patch bases |
| Append item/event ID | Stable application or stream identity | Makes append/live delivery idempotent across retries and reconnects |

Generated descriptor collisions fail the build. Descriptor IDs are protocol names, not durable database IDs; a declaration/schema change can create a new ID and build-manifest version.

A page-context token carries the epoch, route/descriptor manifest version, issue/expiry time and integrity protection. It contains no authorization decision or sensitive component input. The server can validate it without retaining one object per open page. Authentication and domain authorization run again for each request/subscription.

Rendered IDs use canonical key encoding and an application/deployment integrity key so raw business keys do not leak into DOM IDs. The intended derivation is equivalent to:

```text
base64url(HMAC(key, protocol | epoch | parent | slot | descriptor | canonical-component-key))
```

The exact algorithm/version is protocol metadata. A collision among rendered siblings, including a duplicate explicit key, fails rendering with an actionable source/component path. Cryptographic output collisions are treated as fatal diagnostics, never as “last element wins”. Key rotation either retains a bounded verification key ring or deterministically marks the old page stale.

## Repeated and conditional components

- A singleton child in a generated named slot needs no application key; its parent and slot define identity.
- Repeated patchable siblings require an explicit stable key. List position and iteration order are not identity.
- Multiple instances of the same component type are valid when their parent slot/key path differs.
- Reordering keyed siblings preserves each instance and region ID. Removing then reintroducing the same key in one epoch creates the same logical identity but a later revision; dirty/focus preservation policy decides whether browser-local state returns.
- Duplicate sibling keys fail before output is committed where possible. A late streaming discovery terminates safely and emits only a redacted correlation diagnostic.
- Conditional singleton content retains its slot identity when it reappears, while its target revision continues monotonically within the epoch.

## Replace revision rule

The browser stores `currentRevision` and `newestStartedInteraction` for each target. An enhanced request sends its epoch, affected target IDs, each known base revision and a newly allocated interaction sequence. These values coordinate one browser document and are not trusted for server authorization.

A browser-initiated replace applies only when all are true:

1. protocol/build context and page epoch match the active document;
2. the region ID resolves exactly once in the active registry;
3. its interaction sequence is the newest interaction registered for that target;
4. `baseRevision` equals the browser's current target revision;
5. `nextRevision` is exactly `baseRevision + 1`.

A duplicate or lower sequence/revision is ignored with a safe diagnostic. A base mismatch or forward gap does not guess: the runtime requests a region refresh or full navigation according to the transport capability. Revision overflow creates a new page epoch rather than wrapping.

Initial deferred patches use the page's initial interaction sequence. They cannot overwrite a target for which the user has since started a newer interaction.

### Search race

The search region is at revision 7. The user starts query `ko` as interaction 41, then `kotlin` as interaction 42. Both server calls legitimately start from revision 7 and propose revision 8. Interaction 42 completes first and applies because it is the newest registered intent. Interaction 41 arrives later and is dropped before parsing/mutating its HTML because its interaction sequence is no longer current. No server-side page object is needed.

## Append revision rule

Append is not a replace. Losing an intermediate item can lose information, so append requires:

- the active page epoch and exact known target;
- a named stream/action source and stable item/event ID;
- the next contiguous source sequence and matching target base revision;
- deduplication of an already applied item/event ID;
- a bounded gap buffer, followed by resubscription/region replacement when a gap does not close.

An exact duplicate is an idempotent no-op. A stale sequence is dropped. A gap is never silently skipped. A complete replace can resynchronize the region and advance its target revision, after which old append frames cannot apply. Append item identity is separate from rendered component keys, though an application may deliberately derive both from the same domain ID.

## Navigation, old pages and deployments

| Situation | Deterministic outcome |
| --- | --- |
| Complete or enhanced document navigation | Creates a new page epoch; outstanding old-epoch work is cancelled/ignored |
| Browser back/forward restores a live bfcache document | Its stored epoch remains active locally; the next request is revalidated normally |
| Page token expired or integrity/key verification fails | Enhanced call receives a typed stale-page/reload outcome; safe full navigation remains available |
| Request reaches a deployment that lacks the descriptor/build manifest | No best-effort dispatch; return the typed incompatible-page outcome and canonical reload URL |
| Rolling deployment still recognizes the manifest/token key | Request may complete under its declared protocol contract |
| Unknown target, duplicate target or malformed identity | No DOM mutation; safe diagnostic and resync policy |
| Forged very high interaction/revision value | At most disrupts that attacker's document ordering; it grants no server capability and cannot skip authorization |

The wire encoding and exact status/control frame are decided with framing. The semantic outcome is fixed: an incompatible old page reloads safely rather than invoking a similarly named new action or applying a guessed patch.

## Security and privacy constraints

- IDs and signed context are routing/integrity mechanisms, not access-control capabilities.
- Tokens do not embed principal details, form values or raw domain keys. Every request rechecks current authentication and resource authorization.
- Comparison and signature verification use appropriate constant-time primitives in server implementations.
- Logs use correlation IDs and safe descriptor names; page tokens, raw keys and submitted content are redacted.
- A client can lie about known revisions or sequence numbers only to its own view. The server does not use them to authorize, choose another user's resource or bypass business concurrency rules.
- Domain optimistic-lock versions remain domain data. Woge target revisions coordinate rendered output and do not replace database concurrency control.

## Required fixtures

- same component type rendered twice with different keys;
- duplicate key at one sibling scope and the same key under different parents;
- reorder, remove/reinsert and conditional singleton rendering;
- two out-of-order search responses from one base revision;
- duplicate append, append gap and replace-after-gap recovery;
- late deferred patch after a newer action;
- full navigation while old work is in flight;
- expired/forged epoch token and unknown rolling-deployment manifest;
- forged target/revision values proving authorization is still independent.

# ADR 0062: Register latest intent and bound region recovery

- Status: Accepted
- Date: 2026-10-10
- Decision owners: Woge maintainers
- Related issues: [#37](https://github.com/christian-draeger/woge/issues/37)
- Builds on: [ADR 0010](0010-identity-epochs-and-revisions.md), [ADR 0047](0047-canonical-failure-and-recovery-model.md)

## Context

Two searches can finish in the opposite order from which the user started them. Both can propose
the same next revision. Checking only the revision after a response arrives cannot express the
newest intent. A revision gap instead needs current server content, without retrying a mutation
or letting recovery loop.

## Decision

Expose `runtime.beginInteraction(targets)`. It validates every declared target before changing
ordering state, assigns one monotonic sequence and returns frozen page epoch, sequence and known
target revision context. The caller starts its request afterward and passes that context through
its typed request contract. Context grants no authorization and does not replace domain versions.
At most 128 distinct targets belong to one interaction. Sequence overflow requires a fresh page.

Lower interaction sequences and duplicate/lower resulting revisions are ignored before parsing
fragment HTML or dispatching DOM lifecycle events. Other valid targets in the same stream continue.
The observer reports `outcome: "stale"` with `WOGE_STALE_PATCH`; terminal patch counts count wire
frames, including ignored ones. Different epochs, forward revision gaps and unknown targets retain
their separate existing error/recovery classifications.

Completion adds `stalePatchCount` only when frames were ignored. Enhanced validation does not
restore its summary focus after superseded work; the user's newer interaction retains focus.

Expose `runtime.refetchRegion(target, load, { signal })` for an explicitly supplied safe loader.
The loader receives newly registered intent and current revision context and returns a patch byte
stream. It must issue an authorized, side-effect-free GET for current domain data, not repeat a POST.
The result must be one Replace for the declared target and exact requesting context. It goes through
the normal protocol, HTML and browser-ownership validation, so recovery cannot overwrite newer intent.

The server prepares that one Replace with `regionRefresh(fallback, target, input, revision, interaction)`.
It reuses the typed replacement renderer and pre-stream validation, not a second patch API.
All three page adapters negotiate this result through `Accept` on a normal GET; requests without
patch-stream Accept redirect to the visible full-page fallback. Ordinary page redirects are unchanged.
The application loads and authorizes current data before calling the helper. A client's base revision
is its DOM position, not the authoritative domain version.

Allow one recovery attempt per target revision, including failed or cancelled attempts after loading
starts. Keep at most 128 target budget entries per runtime, replacing each entry on a later revision.
Exhaustion fails closed with `WOGE_RESYNC_EXHAUSTED`. Unknown removed targets cannot be guessed back
into the registry; applications can choose their bounded full-navigation fallback.

Full-navigation recovery stores one tab-local attempt for the current full page URL, not a key for
each epoch. A reload creates a fresh epoch, so an epoch-only budget would allow an endless reload
loop. A different page URL replaces the stored attempt; unavailable storage disables auto-reload.

## Alternatives considered

- **Only compare response revisions:** cannot distinguish two requests from the same base.
- **Change interaction attributes directly:** bypasses the registry's integrity checks.
- **Retry the original request on a gap:** unsafe for mutations and does not load current state.
- **Hard-code region URLs in the client:** hides host routing and cannot enforce domain authorization.

## Consequences

Public APIs remain ordinary request/response and typed ordering context. No protocol version,
transaction mechanism or automatic form replay is added. Existing native forms remain unchanged;
an application opts into latest-intent context explicitly. Duplicate frames now finish successfully
as ignored stale work instead of rejecting the rest of an otherwise valid stream.

A patch stream is not atomic. A malformed later frame still cannot roll back an earlier applied
replacement. Recovery never grants such a guarantee.

## Evidence

Client fixtures cover same-base search races, stale deferred work, duplicate collection frames,
independent targets, bounded recovery, invalid scope and cancellation. The reference Task Board
adds a generated, read-only region GET carrying epoch, target, base revision and interaction.
It resolves only its explicitly supported task-list target and never mutates the board.
Browser journeys run the same real GET search race and repeated-submit response gate on
Spring WebFlux, Spring MVC and Ktor, without server sleeps or replaying a mutation.

## Follow-up

Request authenticity, domain authorization and mutation replay identities remain separate work in
[#34](https://github.com/christian-draeger/woge/issues/34). The refresh helper is not a security policy.

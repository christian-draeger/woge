# ADR 0047: Map every failure to exactly one bounded recovery outcome

- Status: Accepted
- Date: 2026-10-09
- Decision owners: Woge maintainers
- Related issues: [#124](https://github.com/christian-draeger/woge/issues/124), [#31](https://github.com/christian-draeger/woge/issues/31), [#37](https://github.com/christian-draeger/woge/issues/37), [#38](https://github.com/christian-draeger/woge/issues/38), [#120](https://github.com/christian-draeger/woge/issues/120)

## Context

Woge has several paths that can fail: normal page loads and form posts, enhanced Fetch requests,
streamed patches for deferred regions and, later, live updates. Until now each part had its own
error codes: the JVM codec ([ADR 0024](0024-strict-bounded-patch-stream-codec.md)), the browser
runtime ([ADR 0025](0025-browser-replace-runtime-and-lifecycle.md)) and the identity rules
([ADR 0010](0010-identity-epochs-and-revisions.md)). Nobody decided what the application should *do*
with each code. Without one answer, applications invent their own retries, reload loops or silent
failures. Retrying a form post can create a record twice.

Forms, actions and live updates are still being built in M2. Their public behavior should follow one
model from the start instead of being aligned afterwards.

## Decision

Woge has one failure taxonomy with twelve categories: compile-time diagnostics, request decoding,
CSRF/authentication/authorization ("security"), domain conflict, rendering, transport, protocol,
stale or missing revision, incompatible client, browser apply failure, resource exhaustion and
cancellation. The [failure and recovery model](../architecture/failure-and-recovery.md) maps every
failure to exactly one of six bounded outcomes:

- `fail-closed`: apply nothing and keep the current, working page;
- `error-response`: the server returns an ordinary HTML status page or re-rendered form;
- `ignore-stale`: drop superseded or cancelled work;
- `refetch-region`: ask for the current state of one region;
- `reload-page`: load the page again;
- `retry-safe`: send the request once more.

These rules are fixed:

1. **No automatic replay of unsafe requests.** Only a request the caller marks as safe (an
   idempotent GET without side effects, such as loading deferred regions) can get `retry-safe`.
   HTTP error statuses are never retried. A future retry for mutations needs its own idempotency
   ADR.
2. **Recovery is bounded.** `reload-page` runs at most once per page epoch and browser tab;
   `retry-safe` runs at most once per request. Without session storage there is no automatic
   reload at all.
3. **The server decides about reload after commit.** A terminal Error frame carries `reload` or
   `none`. The browser follows only that permission.
4. **Native and enhanced paths mean the same thing.** The user sees the same validation message,
   status page or current data in both paths; only the mechanics differ.
5. **Diagnostics are safe.** A classification contains only a stable code, the category and the
   outcome. It never includes request data, HTML, tokens or exception messages.

Responsibilities:

- **Server core** decodes, validates and renders. A failure after the response started becomes one
  terminal Error frame with a stable code, a correlation ID and the allowed recovery.
- **Host adapters** run CSRF, authentication and authorization through the host, choose HTTP status
  codes and cancel work when the client disconnects.
- **Browser runtime** validates before it touches the DOM. The fallback client exports
  `classifyWogeFailure(problem, { safeRequest })` and `createWogeRecoveryBudget()`. Applications and
  later Woge enhancement code use these instead of their own rules. The reference application already
  uses them for deferred regions.

Contract tests in `client/woge-fallback-client/tests/recovery.test.mjs` cover malformed, truncated
(disconnected), stale, missing-target, version-skewed, cancelled, oversized and remote failures,
the HTTP status mapping and the recovery budget.

## Alternatives considered

- **Retry every failed request a few times:** rejected because a form post or other mutation could
  run twice. Retrying is only safe when the request has no side effects.
- **Reload the page on every failure:** rejected because a broken deployment or a hostile response
  would cause a reload loop, and users would lose unsaved input.
- **Let each feature define its own recovery:** rejected because forms, deferred regions and live
  updates would behave differently for the same failure, and applications would copy ad-hoc rules.
- **Put the human-readable error message in the browser classification:** rejected because messages
  can contain secrets or user data. The correlation ID connects to server logs instead.
- **Use exponential backoff for live and Fetch requests now:** deferred. Live-update reconnects belong
  to [#38](https://github.com/christian-draeger/woge/issues/38) and still map to the same outcomes.

## Consequences

### Positive

- Applications and AI agents can look up one table instead of guessing.
- Duplicate mutations cannot be caused by Woge recovery.
- Reload and retry loops are impossible by construction.
- Forms, actions and live updates in M2 build on a fixed vocabulary.

### Negative

- Some failures that a retry would have fixed stay visible as `fail-closed` until the user acts.
- `refetch-region` has no endpoint yet; until [#37](https://github.com/christian-draeger/woge/issues/37)
  applications treat it like `fail-closed`.
- The browser runtime and the JVM must keep the code lists in sync.

## Follow-up

- Show `error-response` for enhanced forms in [#31](https://github.com/christian-draeger/woge/issues/31).
- Implement region refetch for revision gaps in [#37](https://github.com/christian-draeger/woge/issues/37).
- Apply the same outcomes to live-update reconnects in [#38](https://github.com/christian-draeger/woge/issues/38).
- Decide how outcomes are announced to assistive technology in [#120](https://github.com/christian-draeger/woge/issues/120).

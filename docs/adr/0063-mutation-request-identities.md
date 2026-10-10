# ADR 0063: Keep mutation request identities separate

- Status: Accepted
- Date: 2026-10-10
- Decision owners: Woge maintainers
- Related issues: [#34](https://github.com/christian-draeger/woge/issues/34)
- Builds on: [ADR 0062](0062-latest-intent-and-bounded-region-recovery.md)

## Context

A POST can commit even when its response is lost. A host trace ID identifies one HTTP request,
but does not identify a mutation across a deliberately repeated request. Page epochs and revisions
only order browser updates. None of these prove who is allowed to change domain data.

## Decision

The enhanced form client creates one random UUID with the browser's `crypto.randomUUID` for each
submission and sends it in `Woge-Request-Identity`. A busy form does not start another submission.
The client never retries the POST. If secure UUID generation is unavailable, submission remains
native HTML rather than using weak randomness.

All three action adapters strictly parse at most one UUID header after host context creation and
before command decoding. Invalid, empty, repeated or comma-merged values return bodyless 400.
An action context without verified request authenticity returns bodyless 403 before command decoding,
even when it contains an authenticated principal. Hosts must translate the result of their configured
CSRF strategy; authentication alone and `NOT_REQUIRED` page defaults are not accepted by action bindings.
The typed `RequestContext.mutationIdentity` is separate from host trace IDs, authentication,
CSRF verification and domain versions. Its diagnostic representation is redacted.

Native forms may omit the header. Application-owned native replay protection can supply an identity
through its explicit integration; Woge does not rewrite normal form commands or generate a server
identity that could not survive resubmission.

An identity alone is **not idempotency**. Without an explicitly configured, scoped reservation store,
two permitted requests can still mutate twice. The action must always perform current domain
authorization, including when an identity was previously seen.

Expose the optional `MutationReservationStore` infrastructure port, separate from domain interfaces.
Applications reserve by trusted `MutationScope` plus identity and compare `MutationFingerprint`,
a SHA-256 digest of their canonical command. Do not persist raw form values or cached HTML responses.
One atomic reservation grants a store-issued fencing lease; a duplicate reports `IN_PROGRESS`,
`COMPLETED`, `REJECTED`, `AMBIGUOUS`, `EXPIRED` or `CONFLICT` without executing again.
Changed command digests or replay-window timestamps conflict.

The application fixes `expiresAt` and `retainUntil` when it issues a replay window, using trusted
session state or signed context. It must not renew those timestamps on a duplicate request.
Retention extends beyond expiry. After retention cleanup an expired identity still cannot reserve.
An expired in-progress reservation becomes ambiguous, never free for another execution.
Lease-fenced `resolve` records explicit commit facts: completed, rejected without commit, or ambiguous.
Do not infer them from HTTP status, rendering success or delivery of a response.

Applications wire reserve and resolve explicitly after current CSRF and domain authorization.
A storage error stops work and propagates through the host's error path; no success fallback is added.
If a commit may have happened, leave/resolve the reservation ambiguous and inspect authoritative
domain state. A crash or cancellation never releases it for a retry. Coordinating durable domain
commit and reservation storage remains the application's transaction responsibility.

## Alternatives considered

- **Reuse a host trace ID:** generally changes with every request.
- **Reuse the interaction sequence:** orders DOM work, not mutation effects.
- **Automatically retry POST with a UUID:** a UUID does not prove the server reserved or committed it.
- **Hide an extra domain form field:** couples transport identity to every command serializer.

## Consequences

Existing native commands and host CSRF integration remain unchanged. A malformed identity cannot
invoke command or field-error rendering. Current authentication and CSRF facts remain host-owned.
No replay store, response cache or retry behavior is silently installed. The TCK's in-memory store
is test-only; production code never imports it. Native PRG refresh is a GET. Deliberate native
resubmission needs an application-issued replay window/identity; enhanced reconnect also never retries
a POST. A completed duplicate can navigate to freshly authorized domain state instead of replaying HTML.

## Follow-up

The optional store SPI and all-host HTTP duplicate/authorization matrix are implemented. The TCK
also exercises simultaneous reservations, fencing, expiry, retention, changed windows and ambiguous
outcomes with explicit time rather than sleeps. The same native/enhanced CSRF, authorization and
replay matrix runs behind actual Spring Security filter chains on MVC/WebFlux and an explicit
test-only session-token integration on Ktor. Missing, invalid, repeated and expired-session tokens
fail closed. Production applications still choose their durable backend and security/session policy;
no generic database implementation or automatic unsafe retry is added.

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
The typed `RequestContext.mutationIdentity` is separate from host trace IDs, authentication,
CSRF verification and domain versions. Its diagnostic representation is redacted.

Native forms may omit the header. Application-owned native replay protection can supply an identity
through its explicit integration; Woge does not rewrite normal form commands or generate a server
identity that could not survive resubmission.

An identity alone is **not idempotency**. Without an explicitly configured, scoped reservation store,
two permitted requests can still mutate twice. The action must always perform current domain
authorization, including when an identity was previously seen.

## Alternatives considered

- **Reuse a host trace ID:** generally changes with every request.
- **Reuse the interaction sequence:** orders DOM work, not mutation effects.
- **Automatically retry POST with a UUID:** a UUID does not prove the server reserved or committed it.
- **Hide an extra domain form field:** couples transport identity to every command serializer.

## Consequences

Existing native commands and host CSRF integration remain unchanged. A malformed identity cannot
invoke command or field-error rendering. Current authentication and CSRF facts remain host-owned.
No replay store, response cache or retry behavior is silently installed.

## Follow-up

Complete #34 with the optional scoped reservation/resolve store SPI, explicit expiry and retention,
in-progress/completed/rejected/ambiguous outcomes, and the combined all-host CSRF/authorization/replay
matrices. Request identity transport is a foundation, not completion of that issue.

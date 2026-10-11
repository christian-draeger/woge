# ADR 0076: Use a built-in same-origin check for form actions

- Status: Accepted
- Date: 2026-10-11
- Decision owners: @christian-draeger
- Related issues: [#213](https://github.com/christian-draeger/woge/issues/213)
- Builds on: [ADR 0008](0008-security-trust-boundaries.md)

## Context

Woge form actions accept browser POST requests, but adapter defaults previously only mapped safe
methods. An application had to write a request-context factory before a normal same-origin form
could run. That repeated host-specific security wiring and made basic examples harder to follow.

## Decision

1. Each host provides a built-in request-context factory for action handlers. Safe methods need no
   CSRF verification. For unsafe methods, a non-null `Origin` must match the request's origin;
   otherwise, when `Origin` is absent or `null`, `Sec-Fetch-Site: same-origin` can establish the
   same-origin fact. A mismatch or missing evidence remains unverified, and the action handler
   returns 403 before decoding or executing the action.
2. This is the default for action bindings and Spring Boot's default factory beans. It does not
   authenticate a visitor or replace domain authorization. Applications using Spring Security CSRF
   tokens, another token scheme or authentication should provide their own context factory; Spring
   Boot's `@ConditionalOnMissingBean` defaults back off for an application bean.
3. Origin scheme and host comparisons are case-insensitive. Explicit default ports (`:80` for HTTP
   and `:443` for HTTPS) match the equivalent origin without the port.
4. Behind a TLS-terminating proxy, configure trusted forwarded-header handling so the adapter sees
   the public request origin: Spring Boot's `server.forward-headers-strategy=framework` or Ktor's
   `XForwardedHeaders` plugin. Forwarded headers must come only from trusted proxies.

## Alternatives considered

- **Require an application-written factory for every action.** This is explicit but repeats the same
  basic browser-origin policy in every application. Rejected as the default.
- **Accept only `Origin`.** Some browsers and non-browser clients omit it. The Fetch Metadata header
  provides a conservative browser signal when available.
- **Trust `Sec-Fetch-Site` even when Origin disagrees.** A contradictory origin is more specific
  evidence of a cross-origin request, so it is rejected rather than overridden.
- **Install a token-based CSRF system automatically.** Tokens require application session and
  authentication policy, so they remain an application-owned factory.

## Consequences

- A same-origin browser form works with the built-in factory. Cross-origin POSTs and requests with
  no same-origin evidence are rejected as forbidden.
- Non-browser clients must send a matching `Origin` or `Sec-Fetch-Site: same-origin`, or configure
  their own factory.
- The origin observed by the application must reflect the public scheme and host when deployed
  behind a proxy; forwarded headers are trusted only when the proxy boundary is configured.
- This preserves the trust boundary in ADR 0008: CSRF verification is an ingress fact, not an
  authorization decision.

## Follow-up

- Keep the adapter integration tests aligned with the supported Origin and Fetch Metadata behavior.
- Document token-based CSRF and authentication integrations using application-provided factories.

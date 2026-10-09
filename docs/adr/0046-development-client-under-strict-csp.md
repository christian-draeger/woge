# ADR 0046: Keep the development client working under a strict CSP

- Status: Accepted
- Date: 2026-10-09
- Decision owners: @christian-draeger
- Related issues: [#47](https://github.com/christian-draeger/woge/issues/47)
- Builds on: [ADR 0045](0045-wogedev-gradle-launcher-and-development-head-hook.md)

## Context

Many applications send a `Content-Security-Policy` (CSP) header. `wogeDev` adds a small client to
every page: `client.js` and `overlay.css` from the session's loopback origin, plus an `EventSource`
connection to it. A strict policy such as `default-src 'self'` blocks all three, so live reload stops
working exactly in the applications that take security seriously. Asking developers to edit their
policy for development is easy to forget and easy to leak into production.

## Decision

1. **Reuse the page nonce.** When the page gives one of its own head assets a nonce
   (`moduleScript`, `stylesheet` or `style`), `HtmlWriter` remembers the first one.
   `DevelopmentHeadContribution.writeTo(head, nonce)` receives it, and the client puts it on its
   script and stylesheet. Woge never creates a nonce itself, because only the application knows the
   value it sent in the header.
2. **Add the session origin in a dev-only Spring filter.** `woge-dev-spring-child` registers a servlet
   `Filter` (MVC) and a `WebFilter` (WebFlux) through an `ApplicationContextInitializer`, only while
   `WOGE_DEV_CLIENT_FILE` is set. They add the one loopback origin to `script-src`, `style-src` and
   `connect-src` of `Content-Security-Policy` and `Content-Security-Policy-Report-Only`. A missing
   directive starts as a copy of `default-src`; `'none'` is replaced. All other directives stay as
   the application wrote them.
3. **Rewrite at the last moment.** The servlet filter wraps the response, so headers set later by
   the application or Spring Security still pass through it. The WebFlux filter re-applies the
   change after every other before-commit action. The rewrite is idempotent.
4. **The scaffold smoke test proves it.** `scaffoldDevSmoke` adds a `default-src 'self'` filter to
   the generated application and checks the header for both WebFlux and MVC.

## Alternatives considered

- **Ask developers to allow the origin themselves.** This was rejected. The port changes per session,
  and a hand-edited policy can reach production.
- **Generate a Woge nonce.** This was rejected. A nonce only helps if the header carries the same
  value, and the application owns the header.
- **Serve the client from the application origin.** This was rejected for M1. It needs a reverse proxy
  or an in-app endpoint, and ADR 0041 rules out a proxy for M1.
- **Use a `<meta http-equiv>` policy.** This was rejected. A meta policy can only add restrictions,
  never relax a header policy.

## Consequences

- Strict policies keep working in development without code changes. Pages that use nonces only in
  `body` still need one nonced head asset for the client to pick up the nonce; a host allowance alone
  is ignored when the policy uses `'strict-dynamic'`.
- Production is unaffected: the filter and initializer live in a `developmentOnly` module that
  `verifyWogeProductionArtifact` keeps out of the jar.

## Follow-up

- Apply the same header rewrite in the Ktor development host (#154).

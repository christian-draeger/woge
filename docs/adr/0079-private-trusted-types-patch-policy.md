# ADR 0079: Use a private named Trusted Types policy for patch parsing

- Status: Accepted
- Date: 2026-10-11
- Decision owners: Woge maintainers
- Related issues: [#43](https://github.com/christian-draeger/woge/issues/43)
- Builds on: [ADR 0008](0008-security-trust-boundaries.md), [ADR 0078](0078-xss-corpus-and-html-base-boundary.md)

## Context

The fallback client parses server patch strings into inert templates before rejecting active content.
Browsers enforcing `require-trusted-types-for 'script'` also protect the template `innerHTML` sink.
External modules avoid inline execution but do not by themselves make that sink usable.

## Decision

Create a private `woge` policy lazily, once per document realm's Trusted Types factory. It implements
only `createHTML`, wrapping the decoded patch payload for template parsing. Both replace and append
use it; policy objects and customization hooks are not exposed through the client API. The existing
inert-fragment checks still run before DOM mutation. Policy creation denial fails closed, without a
raw-string fallback, a default policy or an additional policy name. Browsers without Trusted Types
retain the same patch checks. Applications reserve the name and load one client copy per document.

Use the existing `CspNonce` and head-asset helpers when an application chooses nonce-based CSP;
introduce no new nonce API. Recommend same-origin external modules and CSS, with no nonce required.
Set a real strict CSP header in every reference host and reuse their existing browser journeys and
CI job rather than adding a new browser matrix. Development-only header rewriting stays separate
from production policy ownership.

## Alternatives considered

- **A permissive default policy:** rejected because it would authorize unrelated application sinks.
- **An application-supplied HTML policy hook:** rejected because components could silently bypass
  patch defaults; unsafe application HTML remains a separate explicit capability.
- **Treat TrustedHTML as sanitized:** rejected because the policy enables inert parsing, not trust
  in stored data or application code. Active-content rejection is still mandatory.
- **Create a policy per runtime:** rejected because strict policy names disallow duplicate creation.

## Consequences

Applications can enforce `trusted-types woge` without inline script exceptions. A denied name or
multiple independent copies fail closed rather than weakening the header. Trusted Types support
is browser-dependent and is defense in depth, not a replacement for typed encoding or authorization.
Tailwind and plain CSS share external stylesheet delivery; no new frontend toolchain is required.

## Follow-up

- Preserve the strict header and violation assertions when adding new reference component behavior.
- Track Ktor development-policy rewrite separately; never make dev origins production defaults.

# ADR 0087: Smooth action signatures without changing public URLs

- Status: Accepted
- Date: 2026-10-11
- Decision owners: Woge maintainers
- Related issues: [#214](https://github.com/christian-draeger/woge/issues/214), [#148](https://github.com/christian-draeger/woge/issues/148)

## Context

The complexity gradient exposed unused request-context parameters and a separate draft-text
workaround for business-rule errors. Features in different packages also collided without enough
source information. Existing action IDs and route paths already identify public wire URLs.

## Decision

A typed action takes a command and optionally a non-null `RequestContext`. KSP generates a direct
call matching that signature. The descriptor and host contracts keep the context, authentication
and CSRF checks unchanged on both Spring and Ktor.

`FormValues.of(Map<String, List<String>>)` copies explicitly selected HTML field names and text.
Applications can pass this immutable, redacted snapshot to `FormErrors` for business-rule rejection.
There is no reflection or automatic echoing. Preserve original string fields before normalization;
use existing `FormValidation.values` for undecodable input. Do not preserve passwords or CSRF tokens.
A decoded command cannot recover raw numeric spelling or field absence.

Keep action IDs and route patterns unique per module, and generated descriptor names unique per
package. Reuse stable `WOGE-ACTION-007`, `WOGE-ROUTE-007` and `WOGE-ROUTE-008` diagnostics, now naming
all conflicting fully qualified declarations with file locations. IDs and URLs do not change.

## Alternatives considered

- Scope IDs or paths silently by package: rejected because refactoring Kotlin packages would change
  public URLs and existing form submissions or links.
- Make every action receive a submission wrapper: rejected because ordinary commands should remain
  direct and typed; explicit snapshots suffice for business-rule text retention.
- Echo every submitted field automatically: rejected because sensitive text must remain an explicit
  application decision.

## Consequences

Command-only actions need no unused-parameter suppression. Native forms remain visible, and a
single errors model replaces the gradient's separate draft argument. Collision errors identify the
conflicting feature without breaking wire compatibility. Applications still select safe fields and
must choose globally unique public IDs; cross-module registry collisions remain runtime checks.

## Follow-up

Keep the complexity gradient baseline and generated application guidance in sync with these APIs.
Retain compiler tests for both signatures and cross-package collisions, plus immutable snapshot and
native business-rule rerender tests. Any future raw-submission API must preserve bounded decoding
and explicit sensitive-field selection rather than silently changing the command contract.

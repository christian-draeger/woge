# ADR 0110: Prepare validation updates through the request render session

- Status: Accepted
- Date: 2026-10-20
- Decision owners: @christian-draeger
- Related issues: [#266](https://github.com/christian-draeger/woge/issues/266)

## Context

A normal validation response needs native 400 HTML and enhanced region replacements with the same
request ordering. The previous notes example prepared patches, extracted a patch interaction/revision,
resumed render identity and rendered replacements a second time. Reusing the same identity registry
for native and enhanced preparation can also produce a duplicate-region error. The Todo consumer
reproduced this friction while its HTTP tests checked native validation.

## Decision

Add `RenderSession.validation(nativePage, summary, limits) { replace(Region, value) }`. It reads
interaction and target revisions from the request and prepares replacements using an independent
identity registry with the same secret/epoch/order. The native document keeps its own registration.
Rendering, patch limits, status 400, headers/cookies and focus-summary semantics retain the existing
contracts. The lower-level `actionValidationUpdates` API remains available.

Default `FormErrors` to empty selected values and an empty error list. This is the safe initial/success
state; submitted values still require explicit application selection.

## Alternatives considered

- **Teach every form the patch codec and revisions:** avoidable work that already caused a consumer
  failure.
- **Remove duplicate identity checks:** would hide invalid repeated regions.
- **Introduce another form framework:** unnecessary; ordinary forms, typed descriptors and existing
  region updates already express the behavior.

## Consequences

The canonical notes validation needs no patch extraction, manual epoch or second render. A new public
method/default constructor path expands the pre-1.0 ABI and is tracked by its ABI dump. Wire behavior
and host bindings do not change. The published-alpha Todo example retains the explicit older path
until a release containing this method is available.

## Follow-up

Verify request revisions/interaction, native document collection, stream limits and reference browser
behavior. Feed this surface to #62 before the compatibility freeze.

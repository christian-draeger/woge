# ADR 0057: Prepare typed action region updates before sending a response

- Status: Accepted
- Date: 2026-10-10
- Decision owners: Woge maintainers
- Related issues: [#31](https://github.com/christian-draeger/woge/issues/31), [#33](https://github.com/christian-draeger/woge/issues/33)
- Refines: [ADR 0056](0056-explicit-action-form-enhancement.md)

## Context

An action can save data and then update several parts of a page. Those updates need the same
typed region functions as the original HTML, not strings containing markup or CSS selectors.
The native form must still work without JavaScript.

## Decision

Expose `actionRegionUpdates(canonicalUrl) { replace(typedTarget, input) }` as a `PageResult`.
The application authorizes and changes data once, then supplies the updated region inputs.
The same result becomes a native 303 redirect or a versioned patch response for an explicit
enhanced action request. MVC, WebFlux and Ktor use this same contract.
The outer HTTP media type accepts standard parameter whitespace and a quoted version value;
wrong or missing versions still fail closed. Binary frame content types keep their canonical format.

Render and validate every replacement before returning the result. Preserve declaration order,
allow each target only once, and require one page epoch. Limit a result to 128 replacements;
each replacement also has the existing protocol payload limits. The result owns an immutable
snapshot. Stream collection only encodes it; it never calls region rendering or the mutation again.

If any replacement fails, preparation fails even if application code catches that exception
inside the builder. No successful subset is returned. The host's existing pre-response error
handling reports the failure. This does **not** roll back an already committed application
transaction. A lost response or rendering failure never triggers an automatic POST replay.
Network delivery and browser application of several patches are not an atomic transaction.

The application supplies the page identity, interaction sequence and each region's current
revision. Initial counters are defaults, not a revision-discovery mechanism. Further updates
must continue the active counters; ordinary command fields are an explicit way to submit them.
Counters and page identity do not grant permission to change data.

Use only the existing `ReplacePatch` format. Append/remove operations, collection identity,
automatic revision synchronization and accessible field-error presentation remain separate work.

## Alternatives considered

- **Render each region after committing HTTP headers:** rejected for action results; a later
  rendering error would expose an avoidable partial response. Deferred page rendering keeps its
  separate streaming behavior.
- **Return raw HTML or selectors:** rejected; reuse typed region targets and the safe HTML DSL.
- **Re-execute the action for a native fallback:** rejected; changing the response representation
  must not save twice.
- **Invent a new patch format:** rejected; the browser already understands validated replacements.

## Consequences

Small action responses have predictable order and fail before sending any patch when preparation
is invalid. Native forms retain POST/Redirect/GET, and enhanced forms can keep focus while updating
document-owned status regions.

Preparing all markup uses memory and delays the first response byte until all regions are ready.
Applications should keep action updates small and recover uncertain outcomes through their own
canonical GET page, not by automatically submitting again.

## Follow-up

Complete collection operations and the reference workflow in #32/#33.
[ADR 0058](0058-shared-accessible-form-errors.md) extends the prepared result with a native HTML
validation representation; its summary-focus policy does not change successful action behavior.

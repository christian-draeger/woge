# ADR 0111: Keep data freshness on server-rendered region boundaries

- Status: Accepted
- Date: 2026-10-20
- Decision owners: @christian-draeger
- Related issues: [#271](https://github.com/christian-draeger/woge/issues/271), [#266](https://github.com/christian-draeger/woge/issues/266)

## Context

Data fetching libraries commonly handle invalidation, background refresh and stale results.
Woge already has authoritative server state, typed action outcomes, ordered region replacement,
SSE invalidation and safe refetch. A second global client object store would introduce ownership and
consistency work without a demonstrated consumer need.

## Decision

Use the [server-driven freshness model](../guides/data-freshness.md) as the default. URL state stays
in URLs, drafts stay in native controls, domain state stays in the application store and visible
current state is rendered HTML. Existing request/region ordering rejects superseded results.
Live refreshes serialize/coalesce and reconnects resync through authorized safe GETs.

The Todo application groups list/count into one board region and keeps the add draft outside it.
Use narrow convenience work for measured gaps, such as request-owned validation. A data-rich
optional island may own a local cache inside its own boundary with explicit invalidation and
reconciliation. No global query cache or offline synchronization is added to core.

## Alternatives considered

- **A React-Query-style domain cache for every page:** duplicates the server/HTML representation
  and requires another stale/auth/invalidation contract.
- **Reload every document for every change:** remains the native recovery path; it unnecessarily
  replaces drafts for simple authorized region updates.
- **Give both an island and Woge the same DOM:** ambiguous ownership when either updates.

## Consequences

The default remains small and web-native. Developers still choose domain transaction, shared store
and event distribution policies. A lost mutation response needs a safe read, not automatic POST
replay; request identities alone are not idempotency. Multi-node behavior needs application-specific
storage and live delivery beyond the process-local Todo fixture.

## Follow-up

Keep the guide connected to Todo and existing live/intent/browser contracts. Add a new subsystem
only if a bounded island or independent application demonstrates an unmet need.

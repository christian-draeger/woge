# ADR 0103: Cache typed component output with explicit security scope

- Status: Accepted
- Date: 2026-10-11
- Decision owners: Woge maintainers
- Related issues: [#59](https://github.com/christian-draeger/woge/issues/59)
- Builds on: [ADR 0012](0012-html-writer-and-kotlinx-interop.md), [ADR 0010](0010-identity-epochs-and-revisions.md), [ADR 0067](0067-safe-http-cache-defaults-and-conditional-pages.md)

## Context

Repeated server rendering of a component can be costly, but sharing rendered HTML across principals or tenants can disclose personalized content. HTTP validators and browser/proxy cache policy do not solve this server-side fragment problem. Component output can also participate in revisioned updates, so a stale result must not roll a client back behind state it has already observed.

## Decision

Add an opt-in `ComponentCache` to `woge-core`. A `ComponentCacheKey` contains a stable component identity, all caller-declared rendering inputs, and a mandatory sealed security scope (`Public`, `Principal`, or `Tenant`). Applications must use immutable value inputs. A caller that knows the current request is personalized must set `requestIsPersonalized`; public scope then fails before rendering.

The cache only accepts a `HtmlWriter` block and rejects output that crosses the explicit `UnsafeHtml` boundary. Its bounded process-local LRU store defaults to 256 entries. Storage is pluggable but must declare a positive entry bound. TTL is positive and measured by an injectable `Clock`. Exact-key and tag invalidation are explicit and prevent an invalidated in-flight fill from repopulating the entry.

Concurrent misses for one key share one fill. Optional stale-while-revalidate returns an expired or behind-current-revision entry only if its revision is at least `minimumRevision`, the revision the caller says this client has already seen. Otherwise the call waits for a fresh render. Background fills are single-flight as well. Cache listener events expose only component identity and event kind, never inputs, scope IDs, or HTML.

This cache is independent of HTTP `Cache-Control`, ETags and intermediary storage. It does not change page-response caching policy.

## Alternatives considered

- **Automatically cache every component:** rejected because applications own output dependencies and security scope.
- **Infer scope from host security context:** rejected because it adds host coupling and hides cache identity from the call site.
- **Use Caffeine:** rejected because it is not currently in the version catalog and the small bounded map is sufficient.
- **Serve any stale revision:** rejected because it can regress a client that has already observed newer state.

## Consequences

### Positive

- Call sites explicitly declare variation inputs, security scope, TTL, revision and invalidation tags.
- Principal and tenant values cannot collide with public or differently scoped entries.
- Deterministic tests can control expiration and background execution without sleeps.

### Negative

- Applications must maintain complete immutable inputs and issue invalidations when non-key dependencies change.
- The cache is process-local; multi-instance invalidation and shared storage are application concerns.
- Custom stores must implement bounded eviction and tag association correctly.

## Follow-up

- Implemented in `modules/woge-core` and documented in [the component caching guide](../guides/component-caching.md).
- Revisit cross-process invalidation only if a concrete shared-cache integration is requested.

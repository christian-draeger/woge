# ADR 0067: Use safe HTTP cache defaults and conditional page responses

- Status: Accepted
- Date: 2026-10-10
- Decision owners: Woge maintainers
- Related issues: [#117](https://github.com/christian-draeger/woge/issues/117)
- Builds on: [ADR 0022](0022-page-host-spi-contract.md), [ADR 0056](0056-explicit-action-form-enhancement.md)

## Context

A browser or intermediary can cache HTML without knowing that it contains user-specific information.
Validators can also incorrectly bypass current authorization or consume a streamed page just to
calculate an ETag. Applications need ordinary HTTP behavior, not a Woge-specific cache store.

## Decision

Page handlers finalize cache metadata through one host-neutral `forHttpRequest` function, after the
application use case and domain authorization, before document frame collection. MVC, WebFlux and
Ktor read conditional fields directly from the HTTP request, independently of custom context factories.

Dynamic results default to `Cache-Control: no-store`. Applications opt into page caching by supplying
ordinary `Cache-Control` headers in `ResponseMetadata`. There is no new cache DSL, server cache store,
automatic response hashing or implicit domain invalidation API.

Unsafe methods, patch results, error responses and responses setting cookies always use no-store.
Their ETag and Last-Modified fields are removed, even when application metadata requests public
caching. POST-redirect-GET remains the native action flow; the subsequent GET has its own cache policy.
Existing patch-stream responses remain no-store. Error and wrong-method paths generated directly by
Woge use no-store; host-generated errors remain the host's configuration responsibility.

An explicitly cacheable 200 HTML document can return a bodyless `PageResult.NotModified` with HTTP
304 for GET or HEAD. `If-None-Match` takes precedence over `If-Modified-Since`, including when malformed
or nonmatching. GET/HEAD entity-tag comparison is weak: `W/"v1"` matches `"v1"`. Lists, commas inside
quoted tags and the standalone wildcard are supported. Conditions over 8192 characters are ignored
before joining fields or parsing; malformed conditions mean a normal authorized response.

With no entity-tag condition, a valid HTTP-date condition matches a Last-Modified value at or before
that date. The implementation accepts RFC 1123, obsolete RFC 850 and asctime HTTP dates; two-digit
RFC 850 years follow HTTP's fifty-year future rule.
Validators are application-owned and describe the selected authorized representation, including
content negotiation and user scope. Woge does not derive them from a lazy response.

A 304 retains cache policy, validators and Vary, but has no HTML content type and never collects
document frames. HEAD uses the same metadata and conditional decision as GET. No conditional request
can replace an authorization failure with 304.

Applications declare normal Vary fields for language and other representation selection. Existing
native/enhanced action negotiation adds `Vary: Accept`; unsafe responses cannot become cacheable.
Personalized HTML that opts into storage should use private caching; public caching is an explicit
application decision, never inferred from an authenticated context or header.

## Alternatives considered

- **Let browsers infer dynamic defaults:** risks caching personalized pages or failed mutations.
- **Compute validators from rendered output:** renders and buffers before deciding not to send it.
- **Evaluate a validator before authorization:** can preserve stale access after permissions change.
- **Introduce a Woge caching model:** duplicates HTTP and hides behavior from normal browser tooling.

## Consequences

The shared real-HTTP TCK proves 200/304 behavior, GET/HEAD parity, metadata retention, weak/list
comparison, condition precedence and current authorization across all hosts. Unit tests cover invalid
conditions, defaults and explicit cookie/error/unsafe overrides. Existing frame budgets remain attached
to cacheable documents.

This intentionally changes implicit dynamic response caching to no-store in the pre-beta API.
Adding a sealed result case requires exhaustive application switches to include `NotModified`.
Public ABI baselines record the additional case and helper.

## Follow-up

#117 remains open for dedicated proxy/CDN fixtures and the static
asset integration with #170. Production SSE must use no-store when introduced. Region/component
memoization and invalidation belong to #59 and are not implied by conditional HTTP pages.

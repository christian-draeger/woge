# ADR 0055: Await reactive security facts when creating a WebFlux context

- Status: Accepted
- Date: 2026-10-10
- Decision owners: Woge maintainers
- Related issues: [#30](https://github.com/christian-draeger/woge/issues/30)
- Refines: [ADR 0028](0028-functional-spring-webflux-adapter.md)

## Context

Spring Security exposes a WebFlux request's principal through a reactive publisher. A synchronous
context factory cannot read it reliably without blocking the event-loop thread or relying on
application-specific request attributes. The actual Spring Security integration test exposed this
limitation.

## Decision

Make `WebFluxRequestContextFactory.create` a suspending function. The existing page and deferred
handlers already suspend and await context creation before decoding or executing application code.
Applications can use `request.principal().awaitSingle()` after their security filter chain has
established authentication and CSRF policy. Errors and cancellation propagate normally.

Spring MVC remains synchronous because Servlet request principals are directly available. Woge
does not add a production Spring Security dependency or infer that an endpoint is secure merely
because a principal exists. The application still configures ingress and domain authorization.

## Alternatives considered

- **Block on the principal publisher:** rejected; it can deadlock or fail on an event-loop thread.
- **Read a thread-local security context:** rejected; reactive context is not thread-local state.
- **Force every application to copy principals into attributes first:** rejected; unnecessary
  middleware and an implicit integration convention.
- **Build a parallel async context-factory API:** rejected before beta; duplicates a small public
  boundary instead of correcting it.

## Consequences

### Positive

- Reactive identity is translated without blocking or runtime magic.
- Authentication finishes before bounded decoding and action execution.
- Existing factory lambdas continue to compile unchanged.

### Negative

- This is a pre-beta binary API change. Explicit implementations must add `suspend` to `create`.
- Direct calls must run in a coroutine; existing Woge handlers already do.

## Follow-up

Keep real Security filter-chain tests for MVC and WebFlux. Their native hidden-field configuration
reads a bounded submission once before CSRF verification and saves it as request-owned input.
The Security token resolver reads exactly one token from that input, never a merged query parameter
or an unbounded host form collector. After the filter chain, the Woge input binding returns the
saved submission. Domain authorization and validation still run only after ingress verification.

This is explicit application-owned integration using existing public form and input APIs, not a
new Security starter or a Woge-owned authentication policy. Header and hidden-field paths share the
same real-HTTP contract, including rejection of malformed and oversized bodies.

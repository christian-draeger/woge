# ADR 0004: Use one project operations dashboard as the reference application

- Status: Accepted
- Date: 2026-09-03
- Decision owners: Woge maintainers
- Related issues: [#1](https://github.com/christian-draeger/woge/issues/1), [#2](https://github.com/christian-draeger/woge/issues/2), [#24](https://github.com/christian-draeger/woge/issues/24), [#75](https://github.com/christian-draeger/woge/issues/75), [#85](https://github.com/christian-draeger/woge/issues/85)

## Context

Woge needs one stable reference journey that exposes interactions between streaming, actions, concurrency, accessibility, styling and server adapters. Separate toy examples would make each feature look simpler while hiding integration failures and duplicating application code.

The domain should be understandable without specialist knowledge and should grow from the walking skeleton into a polished, data-rich application.

## Decision

Woge uses a **project operations dashboard** as its primary reference application. One project page has an immediate semantic shell and three independently loaded regions:

1. project summary metrics;
2. a filterable task table;
3. recent project activity.

The core mutation journey creates a task with server validation and changes a task status. A successful mutation updates the task table and summary through typed region references. An authorized SSE event appends project activity and announces the update accessibly.

The same application model runs on Spring MVC, Spring WebFlux and Ktor. Spring Boot is the primary quick-start host. Styling begins with plain CSS; the optional Tailwind path and richer component system enhance the same markup and behavior later.

Implementation is staged by milestone. M1 proves the shell and deferred regions, M2 adds actions and live updates, M3 hardens behavior, and M4 adds the complete themed UI. Later stages must not replace the earlier journey with a different application.

## Alternatives considered

- **Commerce storefront:** useful for carts and optimistic updates, but pricing and checkout introduce unrelated domain and security complexity too early.
- **AI assistant:** demonstrates streaming well, but does not naturally cover ordinary forms, tables and no-JavaScript CRUD.
- **Independent feature demos:** make isolated documentation easy, but do not prove that the architecture composes into a real application.
- **Administration CRUD only:** covers forms and tables but provides a weaker natural story for deferred metrics and live activity.

## Consequences

### Positive

- Every milestone improves one recognizable application and shared test fixture.
- The domain covers both simple HTML flows and complex frontend composition.
- Hand-written, Woge, Spring and Ktor implementations can be compared directly.

### Negative

- The sample needs explicit fixture data and deterministic timing controls.
- Access control, uploads and optimistic islands require later extensions to the core journey.
- Documentation must distinguish currently implemented stages from the final reference design.

## Follow-up

- Maintain the journey and measurements in the [reference application specification](../product/reference-application.md).
- Build the hand-written baseline in [#2](https://github.com/christian-draeger/woge/issues/2).
- Deliver the first multi-host slice in [#24](https://github.com/christian-draeger/woge/issues/24).

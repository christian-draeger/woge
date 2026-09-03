# ADR 0001: Keep Woge web-native and progressively enhanced

- Status: Accepted
- Date: 2026-09-03
- Decision owners: Woge maintainers
- Related issues: [#1](https://github.com/christian-draeger/woge/issues/1), [#5](https://github.com/christian-draeger/woge/issues/5), [#11](https://github.com/christian-draeger/woge/issues/11), [#75](https://github.com/christian-draeger/woge/issues/75)

## Context

Woge should give Kotlin developers typed, reactive server-driven applications without asking web developers to replace their knowledge of HTML, CSS, HTTP and browser behavior with a mobile UI mental model. Reactive convenience must not make basic navigation and mutations dependent on a large client runtime.

## Decision

Woge uses standards-shaped HTML, ordinary URLs, links and forms as its baseline. The server owns authoritative application state. A small browser runtime may enhance eligible interactions with streamed patches, but core workflows retain a useful no-JavaScript path.

Application code can use normal classes, CSS, custom properties, data and ARIA attributes, custom elements and JavaScript modules. Woge will not introduce a virtual DOM, mandatory hydration graph or Kotlin replacement for CSS.

## Alternatives considered

- **Client-rendered SPA core:** rejected because it makes JavaScript, hydration and a client state model foundational.
- **Compose-style universal UI model:** rejected because it hides browser semantics and conflicts with the intended web-developer mental model.
- **Server-only pages without enhancement:** rejected because it does not meet the reactive interaction and streaming goals.

## Consequences

### Positive

- Browser behavior remains inspectable and interoperable.
- Accessibility, caching, navigation and forms can build on platform semantics.
- Enhancement can fail back to a normal web request.

### Negative

- Native and enhanced paths both need conformance tests.
- Some SPA-style local interactions require an explicit optional escape hatch.
- Patch ownership and preservation rules must be specified carefully.

## Follow-up

- Define browser support and progressive enhancement in [#11](https://github.com/christian-draeger/woge/issues/11).
- Prove styling and complex-application escape hatches in [#75](https://github.com/christian-draeger/woge/issues/75).

# ADR 0051: Generate typed page routes from the page input

- Status: Accepted
- Date: 2026-10-10
- Decision owners: Woge maintainers
- Related issues: [#27](https://github.com/christian-draeger/woge/issues/27)
- Refines: [ADR 0011](0011-typed-web-references.md), [ADR 0049](0049-generated-region-descriptors.md), [ADR 0050](0050-ksp-inside-wogedev.md)

## Context

Before this decision every host decoded its URLs by hand: `request.pathVariable("project")` in
WebFlux, a Servlet attribute in MVC, `call.parameters` in Ktor, plus a small `parseView` function in
each. Links were string templates such as `"/projects/${slug}?view=complete"`. A renamed parameter or
a new enum value could break a link or one host without any compile error.

[ADR 0011](0011-typed-web-references.md) asks for typed web references. Routes must stay ordinary
URLs, work without JavaScript, and keep Spring and Ktor types out of portable page code.

## Decision

**1. The route is declared on the page input.** `@WogeRoute("/projects/{project}")` marks the input
class. `{name}` segments are path parameters and need a non-null property with that name. All other
constructor properties are query parameters and must be nullable; a missing or empty value is
`null`. An object works for pages without parameters.

**2. KSP generates one descriptor per input.** `ProjectPageInput` generates
`object ProjectPageRoute : PageRoute<ProjectPageInput>`. It has `path`, `url(input)` for links and
`decode(parameters)` for requests. The descriptor contains no Spring, Servlet, Reactor or Ktor type.

**3. One value format for every host.** `woge-host-spi` owns encoding: percent-encoding, enum
constants as lowercase with dashes, UUIDs in their standard form. Supported value types are `String`,
`Int`, `Long`, `Boolean`, `UUID`, enums and value classes wrapping one of them. Other types fail the
build instead of guessing a format.

**4. Hosts keep their router; the route supplies pattern and decoder.** Each adapter adds
`handlers.page(useCase, route)` and a `route.xInput()` bridge for deferred streams. Applications still
write `GET(ProjectPageRoute.path, …)`, a `SimpleUrlHandlerMapping` entry or a Ktor `get(…)`. The
generic `PageRoute<I>` and `PageUseCase<I>` must agree, so the compiler rejects mismatched wiring.
The shared adapter TCK proves the same decoding and failure statuses for MVC, WebFlux and Ktor.

**5. A wrong value is a normal failure.** A path value that does not fit means the page does not
exist: `404`. A query value that does not fit is `400`. Both use the existing bodyless
`PageResult.Failure` mapping. The received value is never part of the message.

**6. Mistakes fail the build.** Rules `WOGE-ROUTE-001` to `WOGE-ROUTE-008` cover path syntax,
target shape, missing path properties, non-null query properties, unsupported value types,
visibility, duplicate descriptor names and colliding patterns. Patterns collide when they are equal
after removing parameter names; a literal segment such as `/projects/new` does not collide with
`/projects/{project}`. Route files are aggregating KSP outputs, so every build sees all routes of the
module for the collision check. Region files stay isolating.

## Alternatives considered

- **Annotate a page function or Spring controller:** rejected; it ties routes to one host style.
- **A central route registry or router DSL owned by Woge:** rejected; Spring and Ktor already have
  routers that developers know, and a second one hides the HTTP surface.
- **Non-null query parameters with defaults:** rejected for now; KSP cannot read default values, so
  generated code could not omit them from links.
- **Runtime reflection decoding:** rejected; errors would appear only at request time.

## Consequences

### Positive

- Links and request decoding cannot drift; renaming a property is a compile-time change.
- Host route files shrink to the visible path list.
- Full-page routing needs no enhancement runtime and no JavaScript.

### Negative

- Query properties are nullable, so pages write `view ?: ProjectPageView.SHELL`.
- Each new route makes KSP reprocess all route files of the module. Routes are few and cheap.
- Collision checks cover one module; routes in different modules are not compared.

## Follow-up

- Configurable not-found and error pages without changing component code complete
  [#27](https://github.com/christian-draeger/woge/issues/27).
- Typed actions ([#28](https://github.com/christian-draeger/woge/issues/28)) reuse the value codecs.

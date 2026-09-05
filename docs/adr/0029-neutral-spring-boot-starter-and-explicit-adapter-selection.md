# ADR 0029: Keep the Spring Boot starter neutral and adapter selection explicit

- Status: Accepted
- Date: 2026-09-05
- Decision owners: Woge maintainers
- Related issues: [#69](https://github.com/christian-draeger/woge/issues/69), [#66](https://github.com/christian-draeger/woge/issues/66), [#67](https://github.com/christian-draeger/woge/issues/67), [#73](https://github.com/christian-draeger/woge/issues/73)

## Context

Woge needs an idiomatic Spring Boot entry point without making Spring the portable application
abstraction. Spring MVC and WebFlux are separate first-class adapters with different execution and
response lifecycles. A shared starter cannot transitively include both without making Boot's
classpath inference ambiguous and charging every application for an unused web stack.

Spring Boot also expects external auto-configuration to be registered explicitly, guarded by
classpath, web-application and missing-bean conditions, and tested against representative application
contexts. Woge needs those conventions while preserving familiar application-owned functional routes.

## Decision

`woge-spring-boot-starter` is a transport-neutral dependency entry point. It includes
`woge-spring-boot-autoconfigure` but neither Spring WebFlux, Spring MVC nor either Woge transport
adapter. A consumer adds exactly one Spring web starter and its matching Woge adapter. The reference
application and future project generator provide that complete dependency pair.

`woge-spring-boot-autoconfigure` is registered through Boot's
`AutoConfiguration.imports` mechanism. It uses the `woge` property namespace and initially exposes
only three proven settings: adapter selection, per-request deferred concurrency and per-region
timeout. The configuration processor publishes descriptions and types for IDE completion.

`woge.adapter=auto` accepts exactly one supported Spring web stack and matching Woge adapter. If MVC
and WebFlux are both present, startup fails with an instruction to select `webflux` or `mvc`. An
explicit selection must still match the actual Boot web application context. Missing and unsupported
adapter artifacts also fail at startup with the required artifact name.

Auto-configuration supplies a safe default `WebFluxRequestContextFactory` and one
`WogeWebFluxHandlers` factory. Both back off when an application supplies a bean. The factory carries
the shared deferred execution policy into route-local typed page and patch handlers. It does not hide
routes, path decoding or HTTP methods.

Spring bean discovery records `PageUseCase` and `DeferredRegionsUseCase` entry points for diagnostics
and future generated route descriptors. HTML components are not Spring-scanned: they remain ordinary
typed Kotlin functions and values composed by application code. This avoids a second component model
and keeps their behavior visible to the compiler and coding tools.

Startup logs and an injectable `WogeRuntimeInfo` report the selected adapter, patch protocol version,
Woge artifact version and discovered use-case counts. Context-runner tests cover activation,
application overrides, property binding, non-web back-off, missing artifacts and ambiguous classpaths.

## Alternatives considered

- **Bundle MVC and WebFlux in one starter:** rejected because selection becomes classpath-order
  dependent and both stacks become transitive runtime cost.
- **Make the shared starter WebFlux-opinionated:** rejected because its generic name would make future
  MVC support look secondary and changing that dependency later would be surprising.
- **Silently follow Boot's preferred stack when both are present:** rejected because adding an
  unrelated dependency could change Woge's transport semantics.
- **Auto-scan HTML components:** rejected because components do not need runtime identity or a Spring
  lifecycle; page and deferred use cases are the actual host entry points.
- **Generate routes in auto-configuration:** deferred until typed route descriptors define paths,
  methods and input decoding without string reflection.

## Consequences

### Positive

- Applications choose one transport deliberately and never receive both web stacks from Woge.
- Spring defaults are convenient but all security/context and handler policies remain replaceable.
- Routes still read like ordinary WebFlux or MVC code and can use the full host framework.
- Startup failures identify dependency or application-type mismatches before serving traffic.
- Configuration metadata helps IntelliJ users and coding models discover the small supported surface.

### Negative

- Until an opinionated generated project exists, setup requires the neutral starter plus one Woge
  adapter and one Spring web starter.
- MVC selection remains an actionable unsupported setup until [#67](https://github.com/christian-draeger/woge/issues/67)
  implements and marks that adapter.
- Bean-name discovery does not yet provide typed route metadata.

## Follow-up

- Implement the equivalent MVC handler factory and conditional configuration in
  [#67](https://github.com/christian-draeger/woge/issues/67).
- Replace hand-written path wiring with generated typed route descriptors in
  [#27](https://github.com/christian-draeger/woge/issues/27).
- Put the complete Spring WebFlux dependency set in the executable quick-start and scaffold in
  [#73](https://github.com/christian-draeger/woge/issues/73).

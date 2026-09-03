# ADR 0002: Put server frameworks behind Woge host adapters

- Status: Accepted
- Date: 2026-09-03
- Decision owners: Woge maintainers
- Related issues: [#9](https://github.com/christian-draeger/woge/issues/9), [#63](https://github.com/christian-draeger/woge/issues/63), [#64](https://github.com/christian-draeger/woge/issues/64), [#65](https://github.com/christian-draeger/woge/issues/65)

## Context

Spring Boot is the primary integration target, while Ktor remains important for Kotlin-native server applications and as proof that Woge does not depend on one host. Spring MVC, Spring WebFlux and Ktor expose different request, response, streaming and cancellation types.

A generic clone of their HTTP APIs would either leak framework concepts or reduce all adapters to a weak lowest common denominator.

## Decision

The Woge core, protocol and application model own no Spring, Reactor, Servlet or Ktor types. Narrow host ports model Woge use cases such as page execution, action execution and live-update subscriptions. Adapters translate real framework requests, security context and lifecycle into those ports.

Public portable APIs use Woge-owned types and Kotlin coroutine constructs where appropriate. Spring MVC, Spring WebFlux and Ktor must pass a shared adapter technology compatibility kit. Spring Boot is the primary quick-start and production documentation path; Ktor is a first-class alternative.

Concrete port names and signatures remain provisional until exercised by the walking skeleton.

## Alternatives considered

- **Ktor types in the core:** rejected because Spring support would become a translation layer over another framework.
- **Spring types in the core:** rejected for the symmetrical reason and because application components should remain portable.
- **Universal HTTP request/response facade:** rejected because it would recreate framework APIs and obscure useful host capabilities.
- **Separate Woge programming model per host:** rejected because component and protocol semantics would drift.

## Consequences

### Positive

- Application and component code can run unchanged on each supported host.
- Framework-specific lifecycle and security integration stay idiomatic inside adapters.
- The shared TCK makes portability measurable.

### Negative

- Woge must maintain multiple adapters and real-server fixtures early.
- Capabilities that cannot be expressed portably need explicit host escape hatches.
- Streaming and cancellation differences must be documented instead of hidden.

## Follow-up

- Apply the verified [Spring MVC and WebFlux support model](../architecture/spring-support-model.md).
- Implement the host SPI and adapter TCK in [#18](https://github.com/christian-draeger/woge/issues/18) and [#65](https://github.com/christian-draeger/woge/issues/65).

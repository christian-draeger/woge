# ADR 0084: Bridge semantic observations through Spring Boot's optional Micrometer registry

- Status: Accepted
- Date: 2026-10-11
- Decision owners: Woge maintainers
- Related issues: [#58](https://github.com/christian-draeger/woge/issues/58), [#123](https://github.com/christian-draeger/woge/issues/123)
- Builds on: [ADR 0029](0029-neutral-spring-boot-starter-and-explicit-adapter-selection.md), [ADR 0035](0035-framework-neutral-semantic-observation-port.md)

## Context

The portable observation port already reports semantic started/finished events. Spring Boot
applications should not have to write the same metrics/tracing adapter, but plain HTML applications
must not acquire observability dependencies or another registry by default. Deferred regions may
finish on different coroutine threads and overlap within one request.

## Decision

Place the Micrometer bridge in `woge-spring-boot-autoconfigure`, with a compile-only observation
dependency. Activate it only for web applications with Micrometer classes and an `ObservationRegistry`
bean, after Boot's observation auto-configuration and before Woge's no-op fallback. An application
`WogeObserver` bean replaces either default. Do not add Spring, Reactor, Micrometer or OpenTelemetry
dependencies to core, and do not add a direct OpenTelemetry exporter to the starter. Boot's configured
Micrometer tracing bridge supplies OpenTelemetry integration.

Pair active observations by Woge observation ID in a concurrent map. Emit `woge.operation`, with
`operation` and terminal `outcome` as its only low-cardinality dimensions. Safe request/correlation
IDs, page epochs, region and patch IDs are high-cardinality attributes, never metric labels. Stop and
remove observations on every terminal outcome; ignore an unknown or duplicate finish. Isolate
registry-handler failures from application responses.

Do not open a thread-local scope across suspended work. The registry's handlers measure actual
start-to-stop elapsed time. Applications needing the exact semantic duration can provide a custom
observer. This bridge does not promise coroutine parent-span propagation.

Keep the existing explicit authentication/CSRF factory boundary: Spring owns principals, sessions
and authorization policy. Default locale mapping respects Spring's locale resolvers. Asset
configurers use named missing-bean conditions so unrelated Spring configurers do not disable assets.
Startup configuration failures receive a Boot failure analysis. The analyzer is a small Java class because
Spring's factory loader needs `kotlin-reflect` to instantiate Kotlin classes.

## Alternatives considered

- **Require Actuator, Micrometer and OpenTelemetry in the starter:** rejected; optional telemetry
  must not become a mandatory dependency footprint.
- **Create a private observation registry:** rejected; it bypasses Boot's handlers and exporters.
- **Keep only a documentation snippet:** rejected; first-class Spring hosting needs a tested default.
- **Open scopes on operation start and close them on finish:** rejected; coroutine thread changes
  and overlapping regions make thread-local lifetimes unsafe.
- **Infer identity and token verification in the default context:** rejected; authentication facts
  and domain permission decisions must not be confused, and CSRF decisions belong to the host policy.

## Consequences

MVC, WebFlux and Ktor keep the same observation port, page/action APIs and request facts. Only Spring
bootstrap integration gains the optional bridge. Focused context tests verify activation, absence,
application overrides, cardinality and failure isolation without an exporter or heavy test server.
Existing shared adapter and security-form contracts remain the execution and session evidence.

## Follow-up

- Keep the [starter guide](../guides/spring-boot-starter.md) and
  [semantic observability guide](../guides/semantic-observability.md) aligned with the bridge.
- If coroutine parent-span propagation becomes a product requirement, design a separate host-owned
  propagation contract; do not add Reactor or tracing contexts to the portable observation port.
- Extend mapping tests when the semantic event vocabulary or safe correlation fields change.

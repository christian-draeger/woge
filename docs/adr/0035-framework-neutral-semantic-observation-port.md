# ADR 0035: Use a framework-neutral semantic observation port

- Status: Accepted
- Date: 2026-09-06
- Decision owners: @christian-draeger
- Related issues: [#123](https://github.com/christian-draeger/woge/issues/123)

## Context

Woge requests cross application code, a server adapter, patch encoding and the browser. Logs from one
framework cannot describe that lifecycle consistently, and parsing human-readable messages would be
fragile for tests, development tools and AI agents. Spring Boot should integrate naturally with
Micrometer and OpenTelemetry, while the portable host API and Ktor must not depend on either vendor.

Observability must also respect Woge's trust boundaries. Commands, form values, cookies, rendered
HTML, tokens, exception messages and stack traces can contain secrets. Identifiers useful for tracing
have high cardinality and would be dangerous as metric labels.

## Decision

Woge defines `WogeObserver` in `woge-host-spi` as a synchronous, framework-neutral port with a no-op
default. It receives immutable started and finished events. Every pair shares a process-local
`WogeObservationId`; a finished event carries a monotonic duration and one stable outcome.

The operation and outcome names are the bounded semantic vocabulary used for metric dimensions.
`WogeObservationContext` contains only typed request, target and patch identifiers. Those identifiers
are trace correlation, never metric dimensions. The model has no arbitrary attribute map or payload
escape hatch.

The vocabulary reserves page requests, shell rendering, deferred regions, actions, patch encoding,
patch application, live subscriptions and recovery. Implementations emit only operations they
actually perform. The M1 server runtime emits page, shell, deferred and encoding events; the fallback
browser emits patch-application events. Later action, live and recovery work uses the same port.

Spring Boot contributes a conditional no-op bean and injects any application-provided `WogeObserver`
into MVC or WebFlux. Ktor accepts the same port directly. Vendor adapters map these events at the
outside edge. Observer failures are isolated from application traffic.

Production request events and the development lifecycle planned in issue #140 use separate event
types and vocabularies. They may share correlation IDs, but a build/restart event is not a page
request event.

## Alternatives considered

- **Depend directly on Micrometer or OpenTelemetry:** Familiar in some applications, but leaks vendor
  APIs into the portable boundary and makes Ktor or custom telemetry adapters second-class.
- **Emit structured log messages only:** Easy initially, but forces tools and tests to parse text and
  couples behavior to logging configuration.
- **Expose an arbitrary string attribute map:** Flexible, but makes spelling, type safety, redaction
  and metric cardinality impossible to enforce.
- **Use one global event bus:** Convenient discovery, but hides ownership and makes tests and
  multi-application processes interfere with each other.

## Consequences

### Positive

- Spring MVC, WebFlux and Ktor expose one testable semantic lifecycle.
- Metrics, traces, development tools and agents consume values instead of log prose.
- Kotlin types prevent payloads from accidentally entering the standard event model.
- Applications pay almost nothing when no observer is installed.

### Negative

- Vendor adapters must pair started and finished events by observation ID.
- The callback is synchronous, so adapters must enqueue expensive export work themselves.
- New correlation concepts require typed API evolution rather than arbitrary attributes.
- Best-effort isolation means observer exceptions are intentionally not propagated to requests.

## Follow-up

- Implement action and live-subscription events with their corresponding runtime slices.
- Correlate the separate development lifecycle from issue #140 without merging its vocabulary into
  production telemetry.
- Add first-party Micrometer/OpenTelemetry adapters only after real application evidence shows which
  mappings deserve stable artifacts.


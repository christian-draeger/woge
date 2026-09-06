# Observe Woge without parsing logs

Woge reports meaningful web operations as Kotlin values. An observer can feed metrics, traces, a
development overlay or a test without knowing whether Spring MVC, WebFlux or Ktor handled the HTTP
request.

## Install one observer

With Spring Boot, declare a normal bean:

```kotlin
@Bean
fun wogeObserver(meterRegistry: MeterRegistry): WogeObserver =
    WogeObserver { event ->
        if (event is WogeOperationFinished) {
            Timer.builder("woge.operation")
                .tag("operation", event.operation.semanticName)
                .tag("outcome", event.outcome.semanticName)
                .register(meterRegistry)
                .record(event.duration.toJavaDuration())
        }
    }
```

Boot replaces its no-op default and supplies this observer to the selected MVC or WebFlux handler
factory. Ktor applications pass the same value to `WogeKtorHandlers(observer = observer)`.

The snippet shows the important Micrometer mapping rule: only `operation` and `outcome` are metric
tags. Request, page, region and patch IDs belong on a trace span or structured diagnostic record;
using them as tags would create unbounded metric cardinality.

An OpenTelemetry adapter follows the same shape: start a span for `WogeOperationStarted`, retain it by
`observationId`, add typed correlation as span attributes, and end it with the duration and outcome
from the matching `WogeOperationFinished`. Keep export work off the request callback.

## Understand the events

Every operation emits exactly two events:

1. `WogeOperationStarted` identifies the operation and its safe correlation context.
2. `WogeOperationFinished` repeats the observation ID and adds a monotonic duration plus a terminal
   outcome.

Operations may overlap. Pair them by `observationId`, not by arrival order. Cancellation, timeouts,
expected policy rejection, stale browser work and unexpected failure are distinct outcomes.

The current implementation emits:

| Operation | Emitted by | Boundary |
| --- | --- | --- |
| `page.request` | all server adapters | portable page use-case execution |
| `shell.render` | all server adapters | actual cold HTML-frame collection |
| `deferred.region` | shared server runtime | one region's bounded child task |
| `patch.encode` | shared server runtime | one semantic patch encoded for transport |
| `patch.apply` | fallback browser runtime | one validated patch applied or rejected |

`action`, `live.subscription` and `recovery` are stable vocabulary for later slices; Woge does not
pretend to emit them before that behavior exists.

## Observe the browser

The browser API uses plain JavaScript objects with the same semantic names:

```js
const runtime = createWogePatchRuntime(document, {
  observer(event) {
    diagnosticsChannel.postMessage(event);
  },
});
```

Patch events contain only an observation ID, page epoch, target and patch ID. Patch HTML is absent.
A stale epoch finishes with `stale`; other protocol or DOM policy refusals finish with `rejected`.
Observer exceptions cannot stop a valid patch.

## Keep private data elsewhere

The standard context intentionally has no generic attributes, request body, input, HTML, cookie,
token, exception or message field. If an application needs private diagnostics, keep that data in its
secured logging/tracing adapter and link it with the correlation ID. Do not widen the shared event or
send server-only facts to the browser.

See [ADR 0035](../adr/0035-framework-neutral-semantic-observation-port.md) for the architectural
tradeoffs and the [threat model](../security/threat-model.md) for the wider redaction boundary.


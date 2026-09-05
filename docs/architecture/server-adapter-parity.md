# Server-adapter parity matrix

Woge compares observable web outcomes, not framework APIs. The shared
[`woge-adapter-tck`](../../testing/woge-adapter-tck/README.md) application contains no Spring,
Reactor, Servlet or Ktor types. Each adapter supplies only a small harness factory that binds the
same page and deferred-region use cases to a real HTTP server. This executes the boundaries accepted
in [ADR 0005](../adr/0005-server-host-use-case-ports.md),
[ADR 0022](../adr/0022-page-host-spi-contract.md) and
[ADR 0026](../adr/0026-structured-deferred-region-execution.md).

## Canonical journeys

| Contract | Native or no-JavaScript outcome | Enhanced outcome | Recovery outcome | Automated evidence |
| --- | --- | --- | --- | --- |
| `page-get-stream` | Standard GET returns UTF-8 HTML, status, safe headers and cookies | The same HTML is the initial enhancement document | A refresh repeats an ordinary GET | Adapter TCK |
| `page-head` | HEAD exposes GET metadata without body bytes | Asset and route probes use normal HTTP semantics | Clients can probe without rendering | Adapter TCK |
| `page-redirect` | Browser follows an ordinary policy-checked 303 Location | Enhanced code must preserve the same destination | Full navigation remains valid | Adapter TCK; browser actions follow later |
| `page-controlled-failure` | Typed failure produces its stable bodyless status | Enhancement must not reinterpret it as success | Native error navigation remains available | Adapter TCK |
| `page-pre-stream-failure` | Failure before commit becomes a safe 500 without private detail | Enhancement receives an ordinary failed request | Retry is not automatic | Adapter TCK |
| `deferred-completion-order` | The complete-page route is the useful fallback | Shell is visible before independently completed revisioned patches | The user can navigate to the complete page | Adapter TCK plus reference browser gate |
| `deferred-client-abort` | Navigation away closes the old response | Abort cancels outstanding request children | New navigation owns new work | Adapter TCK where the harness exposes aborts |

Status, redirect and patch mechanics differ only when the web mode requires it. Equivalent outcomes
mean the same authorization decision, destination, useful content and failure meaning—not identical
transport bytes.

Closing the real client response is the deterministic TCK trigger for both a browser disconnect and
the host's subsequent failed-write/cancellation path. A future host-specific downstream failure
injection belongs in a focused adapter test when it exposes behavior that cannot be reached by that
HTTP abort.

## Adapter status

| Adapter | Shared source | Real HTTP | Flush/order | Client abort | Status |
| --- | --- | --- | --- | --- | --- |
| Spring Boot WebFlux | Yes | Yes | Yes | Yes | Passing |
| Spring Boot MVC | Yes | Yes | Yes | Failed-write/timeout | Passing |
| Ktor | Required | Required | Required | Required | Planned in [#68](https://github.com/christian-draeger/woge/issues/68) |

WebFlux and MVC are executable consumers of the same fixture. MVC intentionally omits the passive
client-abort capability because Servlet exposes a disconnect only when a later write fails; its
adapter tests and [guide](../guides/spring-mvc-adapter.md) cover that boundary explicitly. An adapter
is not complete when only its own focused tests pass: it must invoke `ServerAdapterContract` from its test suite. The
multi-host reference slice in [#24](https://github.com/christian-draeger/woge/issues/24) turns all
three rows into a CI release gate.

## Additive suites

`AdapterTckExtension` runs after the core page/deferred contract against the same live harness.
Action and CSRF, cache validators, multipart uploads and SSE each add a focused extension only when
their framework-neutral capability exists. Until then they remain absent rather than receiving
placeholder production APIs. Failures use stable `[WOGE-TCK]` messages naming the adapter, contract
and whether the TCK contract, fixture or adapter owns the divergence.

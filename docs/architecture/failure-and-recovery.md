# Failure and recovery model

Things go wrong in web applications: a form has invalid input, a session expires, a deployment
replaces the server while a tab stays open, or the network drops. Woge gives each of these cases
exactly one predictable reaction. This page is the reference; the decision and its reasons are in
[ADR 0047](../adr/0047-canonical-failure-and-recovery-model.md).

## The six outcomes

| Outcome | What happens | Typical case |
| --- | --- | --- |
| `fail-closed` | Nothing new is applied. The current page stays as it is and keeps working without JavaScript. | Malformed or hostile response |
| `error-response` | The server answers with an ordinary HTML status page or re-rendered form. | Invalid form input, missing login |
| `ignore-stale` | The result is dropped because newer work has replaced it. | An older search finishes after a newer one |
| `refetch-region` | Woge asks the server for the current state of one region. | The region revision no longer matches |
| `reload-page` | The browser loads the page again, at most once per page epoch and tab. | A new deployment or protocol version |
| `retry-safe` | The request is sent once more. This happens only for a safe GET without side effects. | Network failure while loading deferred regions |

Woge never repeats a form submission or any other request with side effects on its own. A retry
needs an explicit idempotency contract, and Woge does not have one yet.

## Failure matrix

| Category | Examples | Native page/form | Enhanced request or stream |
| --- | --- | --- | --- |
| Compile-time diagnostics | wrong reference type, invalid HTML context | build fails | build fails |
| Request decoding | missing required field, invalid date | `error-response` (400/422) | `error-response` |
| CSRF, authentication, authorization | expired session, forged token, no access | `error-response` (401/403) | `error-response` |
| Domain conflict | someone else changed the record | `error-response` (409/412) | `error-response` |
| Rendering | exception while rendering | `error-response` (500) before the response starts | Error frame: `reload-page` if the server allowed it, otherwise `fail-closed` |
| Transport | network down, stream cut off | browser shows its normal error | `retry-safe` for a safe GET before any data arrived, otherwise `fail-closed` |
| Protocol | malformed frame, wrong content type | — | `fail-closed` |
| Stale or missing revision | older interaction, revision gap, unknown target | — | `ignore-stale` for superseded work, `refetch-region` for a revision gap or unknown target |
| Incompatible client | old tab after a deployment, other protocol version, old page epoch | normal navigation | `reload-page` |
| Browser apply failure | active content in a patch, region changed by other code | — | `fail-closed` |
| Resource exhaustion | oversized frame, 413, 429, 503 | `error-response` from the host | `fail-closed` |
| Cancellation | user navigates away, newer request aborts older one | — | `ignore-stale` |

Both paths show the user the same meaning. Only the mechanics differ. A blank required field shows
the same error message, whether the form was sent normally or enhanced.

## Who does what

- **Server core** (`woge-core`, `woge-server-runtime`): decodes and validates input and turns
  failures into typed results. A failure after the response has started becomes one terminal Error
  frame with a stable `WOGE_…` code, a correlation ID and the allowed recovery (`reload` or `none`).
- **Host adapter** (Spring MVC, Spring WebFlux, Ktor): runs CSRF, authentication and authorization
  through the host, sets HTTP status codes and stops work when the client disconnects.
- **Browser runtime** (`@woge/fallback-client`): validates every frame before changing the DOM,
  classifies failures with `classifyWogeFailure` and limits automatic recovery with
  `createWogeRecoveryBudget`.

## Diagnostics

A classification contains only `code`, `category` and `outcome`. Woge never puts request data,
HTML, tokens or exception messages into it. Server logs connect a browser report to the server
failure through the correlation ID.

## What is not built yet

- `refetch-region` needs the region refresh endpoint from
  [#37](https://github.com/christian-draeger/woge/issues/37). Until then an application treats it
  like `fail-closed`; the server-rendered content remains usable.
- How an enhanced form shows an `error-response` is part of the form enhancement in
  [#31](https://github.com/christian-draeger/woge/issues/31).
- Live updates (reconnect and duplicate events) follow the same outcomes in
  [#38](https://github.com/christian-draeger/woge/issues/38).

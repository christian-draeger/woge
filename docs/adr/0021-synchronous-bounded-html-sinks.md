# ADR 0021: Keep HTML sinks synchronous, bounded and transport-neutral

- Status: Accepted
- Date: 2026-09-04
- Decision owners: Woge maintainers
- Related issues: [#17](https://github.com/christian-draeger/woge/issues/17), [#18](https://github.com/christian-draeger/woge/issues/18), [#65](https://github.com/christian-draeger/woge/issues/65)

## Context

[ADR 0012](0012-html-writer-and-kotlinx-interop.md) assigned buffering, incremental output and failure
behavior to `HtmlSink`. [ADR 0005](0005-server-host-use-case-ports.md) separately assigns asynchronous
waiting, backpressure and request cancellation to cold coroutine `Flow` exchanges collected by a host
adapter. The production sink contract must connect these layers without making every HTML tag a
suspending function, building a page-sized string or exposing Servlet, Reactor or Ktor output types.

The initial value implementation also exposed a writer's sink publicly. That would let component code
bypass escaping with an ordinary string, so the rendering entry point needs to preserve the unsafe
boundary established by [ADR 0020](0020-context-specific-html-values.md).

## Decision

`HtmlSink.write` remains a small synchronous operation. One render step is CPU-bound serialization;
deferred application work and asynchronous transport readiness remain outside it. The contract does
not assign network-frame meaning to an invocation and does not own response close or host flush.

Application/component code enters rendering through `renderHtml`, `writeHtml` or `streamHtml`.
`HtmlWriter` remains a public receiver type for reusable component functions, but its constructor and
sink are not public. An ordinary component therefore cannot call the raw sink as a shortcut around
`text` and `UnsafeHtml`.

`BufferedHtmlSink` retains the complete fragment in a `StringBuilder` and exposes a stable snapshot.
It is the implementation behind `renderHtml` and is suitable for unit tests, small fragments and
responses that deliberately do not stream.

`StreamingHtmlSink` coalesces the writer's small calls and forwards ordered chunks to another
`HtmlSink`. It retains at most `maxChunkChars` UTF-16 code units in normal operation. A supplementary
Unicode character is never split; when the configured limit is one character, one two-code-unit chunk
is permitted. The default bound is 8 Ki characters. `streamHtml` flushes the final partial chunk only
after a successful render. Callers using the sink directly flush at an explicit render or protocol
boundary.

The streaming sink is fail-stop. It never catches and translates a downstream failure or cancellation
signal. It records the original throwable, rethrows that exact instance and rejects every later write
or flush with the same failure. A render failure after earlier chunks were sent remains partial output;
the trailing buffered content is not emitted and the host adapter owns post-commit diagnostics.

Cancellation does not introduce a Woge token. A coroutine-based adapter captures its request child
job in the downstream writer and checks that job before host writes; a blocking host propagates its
disconnect/write exception. The sink preserves either signal unchanged. Adapter integration and
real-disconnect timing are verified by the TCK rather than simulated as transport behavior in core.

Element nesting is structural through Kotlin receiver blocks. Attributes are completed and validated
before a start tag is sent, and known void/raw-text misuse fails before output. There is no public
open-tag/close-tag state machine whose ordering a caller can corrupt.

JMH fixtures compile as part of `check` and compare buffered and 8-Ki-character streaming sinks at 32
and 512 representative rows. Benchmark execution remains an explicit developer task, not a noisy CI
performance assertion. Results record throughput and normalized allocation with the GC profiler.
The first public `woge-core` declarations are also committed as a Kotlin ABI dump and checked by the
standard build gate.

## Alternatives considered

- **Make every sink write and HTML DSL method `suspend`:** rejected because a render step has no
  asynchronous work of its own, it would spread transport concerns through every component, and the
  host exchange already owns suspension and backpressure.
- **Keep the writer's sink public:** rejected because it provides an unannotated raw-HTML bypass from
  ordinary component code.
- **Always render a complete string before host output:** rejected because it prevents shell-first and
  bounded-memory output for large fragments.
- **Emit every tiny writer token directly:** rejected as the only streaming helper because host write
  overhead dominates useful work; direct `HtmlSink` implementations remain possible when required.
- **Own `OutputStream`, Servlet, Reactor or Ktor adapters in core:** rejected because character
  encoding, transport readiness and response lifecycle belong to the host adapter.
- **Recover and continue after a downstream write failure:** rejected because the host may have
  accepted an unknown prefix and retrying could duplicate or corrupt output.

## Consequences

### Positive

- Buffered tests and bounded incremental output use the same HTML component functions.
- Core remains free of server-framework and coroutine runtime dependencies.
- Backpressure, cancellation and response commit remain at their existing architectural owner.
- Downstream failures cannot silently become a successful truncated document.
- The safe writer no longer exposes an ordinary-string route to its raw sink.
- Benchmarks make allocation/throughput changes measurable without flaky CI thresholds.
- Public sink and writer signature changes become visible in normal code review and CI.

### Negative

- A host adapter must bridge its own asynchronous output mechanism to a synchronous render step.
- Cancellation is observed at downstream writes, so detection latency depends on chunk size and host
  behavior.
- Character chunks still require encoding/allocation in the adapter before network bytes are written.
- A failure after the first emitted chunk cannot change the already committed HTTP status.
- The default chunk size needs validation through the real Spring MVC, WebFlux and Ktor adapters.

## Follow-up

- Exercise the sink from the framework-neutral host SPI in [#18](https://github.com/christian-draeger/woge/issues/18).
- Verify real connection backpressure, disconnects and post-commit failures in [#65](https://github.com/christian-draeger/woge/issues/65), [#66](https://github.com/christian-draeger/woge/issues/66), [#67](https://github.com/christian-draeger/woge/issues/67) and [#68](https://github.com/christian-draeger/woge/issues/68).
- Re-run and compare the [sink baseline](../performance/html-sinks-baseline.md) when sink allocation,
  default chunking or escaping behavior changes materially.

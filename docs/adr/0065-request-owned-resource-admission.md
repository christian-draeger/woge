# ADR 0065: Admit bounded deferred work and browser patch streams

- Status: Accepted
- Date: 2026-10-10
- Decision owners: Woge maintainers
- Related issues: [#122](https://github.com/christian-draeger/woge/issues/122)
- Builds on: [ADR 0026](0026-structured-deferred-region-execution.md), [ADR 0024](0024-strict-bounded-patch-stream-codec.md), [ADR 0047](0047-canonical-failure-and-recovery-model.md)

## Context

Bounding active deferred work does not bound the number of waiting child coroutines. An unlimited
declaration iterable can allocate an unlimited list before a semaphore helps. A slow transport can
also retain completed HTML in a queue. Individual frame-size limits alone do not bound an entire
browser response or the number of simultaneous response decoders.

## Decision

Budget ownership follows the resource lifetime. Each deferred request admits at most 128 regions
by default, with the existing eight active workers and thirty-second per-active-region timeout.
`maxRegions`, `maxConcurrency` and `regionTimeout` are explicit host/runtime settings. Admission
consumes declarations incrementally before launching children or committing patch-stream headers.
It needs only one `hasNext` lookahead to reject; a lazy or infinite iterable cannot allocate an
unbounded declaration list. The application's own eager list construction remains its responsibility.

All hosts use the same runtime admission policy. Overflow rejects the entire deferred request with
bodyless HTTP 503, never truncates the region set, starts content/fallback work or emits a success
terminal frame. This is server work exhaustion, not an oversized submitted HTTP body.
The safe observation carries `DEFERRED_TASK_COUNT` and its configured threshold with `REJECTED`.
Request correlation remains separate; no input, HTML or exception payload enters budget diagnostics.

The deferred result channel is rendezvous-only. Slow downstream writes hold worker permits instead
of accumulating a completed-result queue. At most the configured worker count can retain completed
results awaiting the collector, plus the collector's current result. Cancellation still cancels
active and waiting children; per-region failures/timeouts keep their existing controlled HTML.
This is a runtime queue bound, not a claim about host/socket buffers or total rendered bytes.

Each browser response defaults to at most 16 MiB of wire bytes and 128 patch frames. Each runtime
for one document admits at most eight simultaneous streams, without an admission waiting queue.
Applications can choose explicit positive integer limits; protocol-version frame ceilings cannot
be raised through them. The runtime feeds transport chunks to the decoder in 64 KiB slices and
applies each returned batch synchronously before decoding another. Even one huge Fetch chunk cannot
create an unlimited decoded-event queue.

Byte exhaustion rejects incrementally; patch-count exhaustion rejects at the next patch header
before buffering its payload. Admission/decoding exhaustion cancels and unlocks the response reader,
releases any acquired runtime slot, and throws `WOGE_RESOURCE_LIMIT_EXCEEDED` with only a stable
limit name and threshold. Recovery is `resource-exhaustion` / `fail-closed`, never replay, retry or
automatic reload. Earlier valid DOM updates are not rolled back; native navigation remains available.

## Alternatives considered

- **Only keep the semaphore:** limits active tasks, not declarations or waiting children.
- **Take the first 128 regions:** hides missing updates and manufactures success.
- **Materialize then count:** exhausts memory before enforcing the limit.
- **Keep the default channel buffer:** retains rendered content independently of active-work limits.
- **Bound only each frame:** still allows unlimited total response bytes and concurrent decoders.

## Consequences

Defaults are shared across MVC, WebFlux and Ktor. Applications opting into larger budgets do so
explicitly at handler construction; adapters never choose a weaker default. Custom direct executor
consumers can call `prepare` before response commitment. `execute` also enforces admission on cold
collection so bypassing preparation cannot bypass its resource limit.

The limits reduce resource exposure without changing HTML/HTTP APIs, adding dependencies or
installing a retry queue. Typed diagnostics are additive; public constructor and context ABI changes
are recorded for this pre-release version.

## Follow-up

This implements the deferred admission/pending-result and browser response/decoder parts of #122.
Full page/patch aggregate byte accounting, multipart/upload policy, SSE subscription ownership and
application/session-wide admission remain open. SSE does not yet have a production API; its future
implementation must apply explicit subscription budgets rather than inherit an unlimited registry.
Do not mark #122 complete until those remaining boundaries and exhaustion paths are implemented.

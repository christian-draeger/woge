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

HTML documents carry a positive cumulative byte allowance, defaulting to 16 MiB. `htmlPage` and
`streamingHtmlPage` expose `maxBytes`; all hosts and the direct document writer enforce it on bounded
UTF-8 chunks before retaining or writing them. The allowance belongs to one collection/HTTP response,
not one frame. Exactly-threshold output is valid. Exhaustion stops rendering and later frames,
propagates a safe exception, and reports `PAGE_BYTES` / threshold with `REJECTED` in host observations.
HEAD still skips rendering. Hosts keep their existing safe pre-commit failures and post-commit
stream failure behavior; already written HTML is not replaced or rolled back.

One shared runtime frame renderer serves all three hosts. MVC writes its encoded chunks directly;
WebFlux and Ktor retain one bounded frame for their transport integration. Cancellation is checked
on each DSL writer call. `patchHtml` also enforces the fixed 8 MiB protocol payload ceiling through
bounded chunks before retaining an oversized fragment, rather than discovering it only at encoding.
These guards cannot bound application-owned eager strings, lists, or custom blocking render code.

Server patch responses use `PatchStreamLimits`: 16 MiB total wire bytes and 128 patches by default,
including framing and completion. A shared incremental budget checks every encoder write before
retaining it; count admission happens before the next patch is encoded. Each cold collection owns
fresh accounting. Exhaustion propagates a typed `ResourceLimitException`, records
`PATCH_STREAM_BYTES` or `PATCH_COUNT` / threshold with `REJECTED`, and never creates a successful
terminal frame. Request cancellation still cancels structured deferred work.

Deferred handlers expose the same `patchStreamLimits` override in all hosts. Prepared action updates
apply it during construction, including the completion frame, rather than retaining an unlimited
set of individually valid fragments. `actionRegionUpdates`, `actionValidationUpdates` and
`regionRefresh` carry the chosen limits with their result to the transport encoder. A caught builder
failure cannot turn incomplete preparation into success. Applications raising server limits must
also explicitly align browser limits; exhaustion is not permission to repeat a mutation.

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

This implements deferred admission/pending results, HTML response bytes, patch fragment/stream bytes
and browser response/decoder parts of #122. Multipart/upload policy, SSE subscription ownership and
application/session-wide admission remain open. SSE does not yet have a production API; its future
implementation must apply explicit subscription budgets rather than inherit an unlimited registry.
Do not mark #122 complete until those remaining boundaries and exhaustion paths are implemented.

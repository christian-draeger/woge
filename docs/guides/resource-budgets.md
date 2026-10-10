# Resource budgets

Limits make expensive requests stop predictably instead of exhausting a server or browser.
They are not authorization and do not make a failed mutation safe to repeat.

## Deferred requests

MVC, WebFlux and Ktor share these defaults:

| Resource | Default | Ownership and overflow |
| --- | --- | --- |
| Region declarations | 128 | One deferred request; reject before work or patch response commitment |
| Active region tasks | 8 | One deferred request; excess admitted work waits for a worker |
| Active region time | 30 seconds | Each active region; cancel and render its controlled timeout HTML |
| Completed-result queue | No extra queue | Slow downstream consumption holds worker permits |

For example, configure `WogeWebFluxHandlers(maxRegions = 64, maxConcurrency = 4)`. The same
arguments work with `WogeSpringMvcHandlers` and `WogeKtorHandlers`. The lower-level executor uses
`DeferredRegionPolicy(maxRegions = 64, maxConcurrency = 4)`. Limits must be positive; timeout must
also be finite. A region's active timeout begins after it acquires a worker, not while waiting.

When declarations exceed `maxRegions`, all three hosts return bodyless 503. No region content or
failure renderer runs, and there is no incomplete stream presented as success. Observers receive a
rejected deferred-operation event with `context.exceededLimit` naming `DEFERRED_TASK_COUNT` and
the configured threshold. The diagnostic contains no declaration inputs or rendered HTML.
Even a lazy infinite iterable stops after the budget and one lookahead. Do not construct an
unbounded eager application list yourself before returning it.

Direct executor integrations should call `executor.prepare(regions, trace)` before committing their
response. Collection through `execute` enforces the same bound again. Cancelling the HTTP request
cancels its active and waiting children. Runtime backpressure bounds completed results to active
workers plus the collector's current result; it does not claim to bound every host/socket buffer.

## Browser responses

```js
const runtime = createWogePatchRuntime(document, {
  limits: {
    maxStreamBytes: 16 * 1024 * 1024,
    maxPatches: 128,
    maxConcurrentStreams: 8,
  },
});
```

These are the defaults. Wire bytes include framing and the terminal frame. Patch count includes
valid stale patches that are ignored in the DOM. Concurrent streams belong to this runtime/document,
not a global browser quota. Use one runtime for the active document.

Limits must be positive safe integers. Protocol version 1 still caps each frame's payload at 8 MiB
and metadata at 64 KiB; runtime overrides cannot raise those frame ceilings. The runtime decodes
large transport chunks in 64 KiB slices and applies one bounded batch before reading the next.

Overflow cancels/unlocks the reader and releases any acquired stream slot. The thrown error has
`code: "WOGE_RESOURCE_LIMIT_EXCEEDED"`, `limit` and `threshold`. Limit names are
`PATCH_STREAM_BYTES`, `PATCH_COUNT` and `CONCURRENT_PATCH_STREAMS`.
`classifyWogeFailure` returns `resource-exhaustion` and `fail-closed`. Never automatically repeat a
POST, retry the rejected stream or reload in a loop. Keep an ordinary authorized navigation link
available. Earlier valid DOM updates are not rolled back.

## Existing input and frame limits

URL-encoded forms already use incremental `FormLimits`: 64 KiB body, 128 fields, 256-byte names
and 16 KiB values by default. `FormDecoder` owns explicit overrides; overflow returns 413 before
command execution. See [typed actions](typed-actions.md).
The versioned patch codec enforces fixed frame ceilings on server and browser; see
[patch streams](patch-stream-codec.md).

## Remaining boundaries

[#122](https://github.com/christian-draeger/woge/issues/122) remains open for full page/patch
aggregate byte accounting, multipart uploads, SSE subscription budgets and explicit application/
session-wide ownership. Do not interpret per-request or per-runtime budgets as process-wide quotas.
The current form decoder does not accept multipart uploads, and no new live-channel API is enabled
by these limits.

# ADR 0027: Fetch deferred patches after the HTML shell

- Status: Accepted
- Date: 2026-09-05
- Decision owners: Woge maintainers
- Related issues: [#23](https://github.com/christian-draeger/woge/issues/23), [#40](https://github.com/christian-draeger/woge/issues/40), [#65](https://github.com/christian-draeger/woge/issues/65), [#66](https://github.com/christian-draeger/woge/issues/66), [#124](https://github.com/christian-draeger/woge/issues/124)

## Context

Woge renders a useful HTML shell immediately and replaces independent regions as their server work
finishes. The fallback browser runtime already consumes an incremental, length-prefixed binary patch
stream. The missing contract is how a stable browser receives both the navigated document and those
bytes without making the HTML response invalid or delaying visible updates until all work completes.

One HTTP response has one media type. Appending binary patch frames after a complete HTML document
would make intermediaries and browsers interpret protocol bytes as document content. Multipart
navigation responses do not provide a dependable cross-browser DOM update mechanism either.

[ADR 0015](0015-limit-native-dpu-to-initial-document-optimization.md) keeps browser-native
Declarative Partial Updates as an optional initial-response optimization. It cannot define the
portable path while support is limited.

## Decision

The stable cross-browser path uses two ordinary HTTP responses:

1. Navigation returns a complete `text/html` document containing useful content, the page epoch and
   semantic loading fallbacks for deferred regions.
2. An external module fetches one page-scoped
   `application/vnd.woge.patch-stream; version=1` response and gives its `ReadableStream` to the Woge
   fallback runtime.

One fetched stream may carry every deferred region for the page. The server emits patches in task
completion order, and the browser applies each complete frame as soon as its bytes arrive. It does
not wait for the terminal frame or HTTP response completion. The terminal frame confirms how many
patches completed the stream.

`woge-server-runtime` maps each `DeferredRegionUpdate` to one `ReplacePatch` using the initial
interaction sequence and one contiguous revision step. Patch IDs are supplied by the request or
adapter boundary; the portable runtime does not invent global identity. The encoder exposes one
fresh `EncodedPatchChunk` per patch and one terminal chunk. A host adapter writes and flushes each
chunk, while arbitrary TCP, proxy and browser byte boundaries remain valid protocol behavior.

The patch endpoint must perform the same authentication and authorization as the document use case.
A page epoch and region ID identify state but never grant access. Concrete URL generation, request
correlation, disconnect handling and post-commit failure mapping belong to host adapters and the
canonical recovery contract in [#124](https://github.com/christian-draeger/woge/issues/124).

Applications must remain useful without the module or Fetch. A no-JavaScript response resolves
required content into normal HTML or offers normal links and form submissions; it must not leave the
only useful result behind a permanent loading state.

Native DPU may later stream shell and patches in one opt-in initial response when the browser and host
adapter support that media type. It remains an adapter over the same semantic regions and patches,
not the baseline protocol.

## Alternatives considered

- **Append binary frames to the HTML response:** rejected because it violates the response media type
  and can expose protocol bytes as document content.
- **Use a multipart navigation response:** rejected because browsers do not offer a dependable,
  interoperable incremental DOM contract for it.
- **Fetch once per region:** rejected as the default because it multiplies requests, authorization
  work and connection pressure. A later specialized adapter may still justify it with evidence.
- **Buffer every patch before returning the Fetch response:** rejected because the first slow region
  would erase the latency benefit and hide proxy buffering regressions.
- **Emit executable inline scripts with each result:** rejected because this couples server output to
  JavaScript execution and weakens strict Content Security Policy compatibility.

## Consequences

### Positive

- The initial response is ordinary inspectable HTML and works with browser, proxy and HTTP tooling.
- Completed regions become visible before slower siblings and before stream termination.
- One patch stream amortizes request and authorization overhead across a page.
- External-module bootstrapping remains compatible with strict Content Security Policy deployments.
- Host adapters share one transport-neutral mapping and explicit flush contract.

### Negative

- Enhanced rendering needs a second HTTP request after navigation.
- The patch endpoint must securely recover or correlate the intended page use case.
- Proxy buffering and disconnect propagation still need adapter-specific integration tests.
- The portable encoder cannot guarantee that an intermediary forwards every flush immediately.

## Follow-up

- Implement and verify the Spring WebFlux endpoint first in
  [#66](https://github.com/christian-draeger/woge/issues/66).
- Reuse the stream lifecycle contract in the adapter TCK in
  [#65](https://github.com/christian-draeger/woge/issues/65).
- Define canonical post-commit failures and browser recovery in
  [#124](https://github.com/christian-draeger/woge/issues/124).
- Add an explicit no-JavaScript acceptance path to the Spring guide in
  [#73](https://github.com/christian-draeger/woge/issues/73).

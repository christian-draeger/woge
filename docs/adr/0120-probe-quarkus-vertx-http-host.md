# ADR 0120: Probe a Quarkus JVM host at the Vert.x HTTP boundary

- Status: Proposed
- Date: 2026-11-25
- Decision owners: Woge maintainers
- Related issues: [#298](https://github.com/christian-draeger/woge/issues/298)

## Context

Woge's portable host boundary carries final response metadata and cold HTML or patch frames.
An adapter needs to send raw chunks, enforce the response budget and tie collection to the
request's cancellation. Quarkus can expose the Vert.x Router through CDI. Its REST facade also
streams Mutiny publishers with a cancellation policy for disconnected clients.
[Quarkus Reactive Routes](https://quarkus.io/guides/reactive-routes/) ·
[Quarkus REST streaming](https://quarkus.io/guides/rest/#streaming-support)

## Decision

Start the JVM integration at the stock Quarkus Vert.x Router, on platform 3.40.1.2 LTS
with Vert.x 4.5.35, Kotlin 2.4.10 and JDK 21. Register routes through a small application CDI
bean. Capture Woge request values before leaving the Netty event loop. Portable work receives
the immutable Woge context and runs on a coroutine worker. Retain host request and response
objects at the adapter edge. Render with Woge's existing UTF-8 budget and transfer one chunk
at a time across a rendezvous to the event loop. Await each socket write and cancel the request
job on disconnect. Reset a committed response after an unexpected generation failure.

The initial module is an isolated consumer spike. It uses Quarkus HTTP and the existing Woge SPI
and server runtime. There is no augmentation extension or Spring and Ktor runtime dependency.
Integrating Quarkus security must explicitly derive immutable principal and origin facts from the
managed request. Off-loop code cannot depend on implicit access to an ambient CDI request scope.
[Quarkus LTS releases](https://quarkus.io/releases/) ·
[Framework-neutral host boundary](0002-framework-neutral-host-boundary.md)

## Verification and follow-up

The pinned JVM app serves the unmodified portable static page and classpath stylesheet. Real
HTTP tests cover HEAD, metadata, early bytes, concurrent responsiveness, the same request trace
after suspension, late HTML byte-budget termination and TCP reset cancellation.
[Quarkus spike](../../spikes/quarkus-host/README.md)

A Quarkus REST bridge remains an option if CDI and REST constraints show a consumer advantage.
Its response filter and exception behavior after streaming begins need the same tests.
The supported adapter decision needs typed actions, authenticated requests, deferred and live
responses, the shared TCK, a standalone consumer outside the spike and explicit native-image
results. JVM evidence applies to the tested matrix.

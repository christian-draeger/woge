# Woge and Hotwire on a two-person shared board

[Narrow comparison #295](https://github.com/christian-draeger/woge/issues/295) · 25 November 2026.
The study compares the maintained Woge Todo, the standalone Hotwire baseline and a Hotwire
variant with a permanent Add form. Each contender uses native HTML, its usual production HTTP
server and its own packaged dependencies. Read the [source and run commands](../../spikes/collaborative-todo/README.md),
[predeclared workload and framework review](../../spikes/collaborative-todo/workload.md) and
[raw rounds](../../spikes/collaborative-todo/evidence/2026-11-25-rounds.json.gz).
The measured fixture source is `1222f9c534c373e41778fdc5510ecb13312efc1c`.

## Observed outcomes

| Correct runs | Woge | Hotwire | Hotwire · stable form |
| --- | --- | --- | --- |
| Native filter, validation and mutations | 3/3 | 3/3 | 3/3 |
| Anonymous and cross-origin controls | 3/3 | 3/3 | 3/3 |
| Convergence across the two selected filters | 3/3 | 2/3 | 3/3 |
| Peer update retains draft, focus and selection | 3/3 | 0/3 | 3/3 |
| Held stable control commits exactly once | 3/3 | 0/3 | 3/3 |
| Removed pressed control issues zero POSTs | 3/3 | 3/3 | 3/3 |
| Transport reopens during the reconnect window | 3/3 | 2/3 | 3/3 |
| Automatic catch-up after reconnect | 3/3 | 0/3 | 0/3 |
| Explicit GET displays the missed row when needed | —, —, — | yes, yes, yes | yes, yes, yes |
| One committed row after uncertain response | 3/3 | 0/3 | 0/3 |
| Revocation denies GET and resubscribe | 3/3 | 3/3 | 3/3 |
| Closed contexts drain both subscriptions | 3/3 | 3/3 | 3/3 |

Both Hotwire configurations use Action Cable WebSockets to carry Turbo's HTML refresh message.
Woge uses SSE invalidations and a managed region refresh GET. The transports are bundled with
different catch-up and DOM policies in this study. In the Hotwire opt-in, the stable form attribute
and one browser lifecycle handler accounted for the improvement in draft and pointer behavior.
The connection and HTTP mutation policies remained the same.

A dash indicates an inapplicable result or a failed measurement. In the second default Hotwire
round the peer did not see the moved row within six seconds. The same round missed the
transport reconnect deadline and recovered the latest board through an explicit GET.

The lost-response relay stopped a response after the upstream application completed it. The driver
issued one user click and never used mutation replay. Logs retain the observed intent counts, real
wire POSTs and the number of rows found during independent safe GETs. The repeated Turbo request
IDs are hashed in the evidence. The two POSTs shared one Turbo request ID in all six Rails runs.
The experiment leaves the cause among the browser, Turbo and the lower-layer network policy
unresolved. Rails recovery through GET exposed both committed rows. All three Woge runs issued
one POST, showed an explicit uncertainty message and recovered one committed row through GET.
The experiment uses process-local stores with no durable idempotency guard. For an application
where duplicates carry a business cost, enforce idempotency at the storage boundary and report
uncertainty without automatically retrying the write. [Woge recovery guide](../architecture/failure-and-recovery.md) · [Durable PetClinic control](../../examples/petclinic-woge/README.md#run-two-petclinic-instances-on-postgresql)

## Descriptive costs

| Metric, round values | Woge | Hotwire | Hotwire · stable form |
| --- | --- | --- | --- |
| App start to listening announcement, ms | 1874, 1430, 1275 | 1139, 1243, 735 | 1177, 1183, 964 |
| Filtered peer edit to visible, ms | 284, 287, 285 | 313, —, 303 | 307, 314, 307 |
| Peer update during a draft to visible, ms | 204, 197, 195 | 801, 304, 799 | 800, 800, 801 |
| Automatic reconnect to visible, ms | 3192, 3203, 3210 | —, —, — | —, —, — |
| Reconnect start through explicit GET to visible, ms | —, —, — | 13318, 12044, 12089 | 11213, 13857, 9236 |
| Wire POSTs after one uncertain activation | 1, 1, 1 | 2, 2, 2 | 2, 2, 2 |
| HTTP body and WebSocket response output after warmup, bytes | 281656, 281666, 281603 | 176428, 169281, 176018 | 228383, 228421, 228421 |
| Peak simultaneously tracked relay subscriptions | 2, 2, 2 | 2, 2, 2 | 2, 2, 2 |
| App RSS with two subscribers, KiB | 332560, 318512, 319840 | 100000, 101008, 100912 | 100496, 100016, 101088 |
| App RSS after the journey before browser close, KiB | 337808, 334208, 326992 | 103360, 104032, 90000 | 103888, 103072, 104624 |
| Check both contexts drained after closing, ms | 4, 3, 4 | 55, 28, 30 | 57, 28, 56 |

Timings use the same Chromium 151 process with fresh contexts, two live subscribers and 40 ms
injected loopback RTT. The visible interval starts before interaction, so it includes automated
input and scheduling. Post-subscription bytes include HTTP response bodies and WebSocket response
bytes at the relay through revocation. They exclude request-side bytes and HTTP headers, and the
number of requests can differ when a path performs a safe recovery GET or an unexpected duplicate.
Closure measures the relay's persistent sockets after closing both contexts. RSS is the application
process, sampled through `ps`, with two browser subscribers and after the journey before closure.

Host was macOS 27.2.1 ARM64 on Mac15,9 with 16 logical CPUs and 64 GiB. Woge ran on Azul OpenJDK
21.0.6 with JVM `-Xms128m`, `-Xmx384m`, and four active processors. Rails 8.1.4.1 ran on Ruby 4.0.6
with Puma 8.0.4 single mode and four server threads. The Ruby process had no comparable heap cap.
Node was 24.13.1. Dependencies and application files are pinned in the fixture. Woge bootJar SHA-256
`9595560bea29a85ac6fb987f6b65bcd626d09d6e0c78e633637afcebcd189d58`, 43,657,908 bytes.
The Rails dependency lock SHA-256 was `6072a5899a0fe1ac7c5a4a5a8873cf92b401ebf68ff2afebf00b80d5d649bb79`.
Shared workstation load was uncontrolled and a local build overlapped some rounds. Both apps
use the same CSS. Its SHA-256 was `73433008368f8842781867eadd8817d30322f4b3e95b12e8b99ea5ffd9f65f00`.

Woge used more response bytes and process RSS than either Hotwire configuration across these rounds.
The two applications have different established framework plumbing and unused features. Rails uses
one controller, one repository and two templates. The stable form variant adds one markup option
and one browser event handler. Woge uses its existing typed action and page sources plus the ordinary
WebFlux registration. The form and board have explicit different lifetimes. These are source
coordination observations. Comparative human implementation time and maintenance cost remain unmeasured.
The [scheduled-arrival study](reference-project-deferred-2026-11-25.md) measures an unrelated
Woge deferred workload. Its arrival rates and thresholds do not transfer to this two-tab trial.

## Decision guide

- **Existing Kotlin and Spring services with an ordinary native workflow.** The Woge Todo's typed
  HTTP actions, separately refreshed regions, and scoped catch-up make it reasonable to try in a
  JVM application that already has persistence and security. Check current domain authorization and
  write idempotency inside that application. Keep stable controls outside replaced content.
- **Existing Rails application with HTML forms.** Hotwire offers Rails-native templates, routing,
  CSRF and broadcast setup. Add explicit DOM preservation where draft loss matters, verify retry
  behavior at the public boundary and define a missed-event resynchronization policy. The scoped
  resource cost favored the Rails process on this workstation. A migration away from its established
  Ruby integration needs independent application evidence.
- **Elixir or Laravel application with different interaction ownership.** A stateful LiveView
  process can reduce app coordination for a connected workflow with frequent view-local events.
  Existing Blade and Laravel validation can favor Livewire's request/snapshot model. Test each on
  its own real workload before making latency, recovery or native-workflow commitments.
  [Primary-documentation capability review](../../spikes/collaborative-todo/workload.md#comparator-selection)

The reproducible evidence applies to the one-process shared board. The authorization admission
control is deliberately common. It tests a denied request and denied subscription from the relay,
using each framework's own CSRF path for the cross-origin control. Production authentication, shared sessions, tenant and record authorization, and durable delivery remain outside this study.
Simultaneous persistent edits to one value require a separate study with matching conflict-resolution rules.

The [pilot observations](../../spikes/collaborative-todo/evidence/2026-11-25-pilot.json.gz) retain the early
draft and lost-response results. Pilot reconnect code counted total readers and could mistake
Bob’s navigation for a failed Alice reconnect. The recorded rounds count subscriptions by
actor and preserve safe GET recovery when a transport times out.

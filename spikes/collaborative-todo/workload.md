# Two-person collaborative Todo v1

Compare an existing Kotlin/JVM team using Woge with an HTML-first Rails team that can use Hotwire
alongside its ordinary controllers and forms. Use pinned packages from this source tree, the
[measurement contract](../../docs/performance/comparison-contract.md) and the stable-control
policy from the [live interaction guide](../../docs/guides/data-freshness.md). The Woge source
baseline is `ade409be945702ecdf4648a6d71eff915efa6a32`, including `14f155f9fb3fd08abfebc73e5476c61e4638e94e`
for #276. Package the unchanged [maintained Todo](../../examples/todo-spring-boot/README.md) against
locally staged `0.1.0-collaboration-study.1` artifacts. The Rails comparator has locked Rails
8.1.4.1, Puma 8.0.4 and Turbo Rails 2.0.23, current supported versions installable with Ruby 4.0.6.

Use separate Chromium contexts for Alice and Bob and the same session names at the test relay for
both arms. A signed secret is generated per Rails process for its built-in cookie session. The
local relay grants both study identities access to the same board and can revoke one for a negative
case. The synthetic relay cookie is only a deterministic fixture identity. The scope covers a shared
board with app-origin checks and one board-level admission rule. Record-level policies, durable
edits, distributed session state, fan-out across replicas and real credential handling are separate.
[Persistent PetClinic topology](../../examples/petclinic-woge/README.md)

## Script and load

Start with a fresh process and ten ordered rows, `Todo 01` to `Todo 10`. Leave the first five active
and complete the other five through their forms. Seed and native checks warm the applications.
Run one two-tab browser journey at a time. Rotate Woge, default Hotwire and Hotwire with a
permanent Add form over three rounds.
Use 20 ms delay in each direction at the relay, two concurrent subscribers, one driven mutation
at a time and no background artificial throughput. Loopback is the physical path. The relay delays
HTTP forwarding and response chunks and WebSocket bytes. The different data paths therefore incur
the scheduling cost of their own additional messages. Compression is off at the relay. Browser asset
and HTTP body bytes plus raw WebSocket response bytes go into traffic totals. Separate the setup,
normal two-user interaction, disconnect, uncertain mutation and access revocation phases.

| Scenario | Required observable outcome |
| --- | --- |
| Native | Disabled JS, filtered useful HTML, escaped title, native POST/redirect/GET, literal invalid input and corrective submission. An ordinary GET is how the native user sees the other session's changes. |
| Authorization | No anonymous GET or write. Shared session revocation denies both the next GET and attempted re-subscription. |
| Filters | Alice's Active and Bob's Completed URLs remain selected while Bob marks a completed row active. Alice sees it and Bob no longer sees it in Completed. |
| Draft | Bob's update appears while Alice's unsubmitted text, focus and selection remain unchanged. |
| Stable activation | An update arrives between pointer down and up on the stable Add control. Exactly one POST and one shared visible result follow. |
| Removed activation | Bob removes the row containing Alice's pressed control. Releasing it issues zero POSTs. |
| Reconnect | Alice disconnects while Bob writes. After the transport reconnects, observe whether it recovers the missed row without input loss. If it stays stale for three seconds after subscription opens, record the gap and verify explicit safe GET recovery. |
| Uncertain write | The relay forwards one POST and consumes the upstream response, closing the browser side before sending headers. Count actual POSTs and check one committed row via safe GET, without retries. Record any visible uncertainty or absent feedback. |
| Cleanup | Closing both contexts leaves zero live relay connections. Measure the process's RSS with both open and after the journey, before context closure. |

End freshness at observation of the expected peer DOM and submit-start at the beginning of the
browser interaction. Record dialogue timing in milliseconds and process RSS from `ps` in KiB.
Record all per-round values and statuses. A visible check has a six-second correctness deadline,
except the twelve-second reconnect attempt and its separate three-second convergence window.
Report samples descriptively, retaining failures and explicit fallbacks. Two live users and three
paired rounds do not resolve performance rankings or cluster capacity. A persistent benchmark would
require equivalent shared stores and deployable event infrastructure for both packages.

## What owns state

| Concern | Woge Todo | Hotwire Todo |
| --- | --- | --- |
| Domain records | Synchronized Kotlin repository in the WebFlux application | Mutex-protected Ruby repository in the Rails application |
| Filter | Typed page URL | Controller query and filter links |
| Draft and focus | Native field outside the mutable board | Native field in the morphed document. An alternate arm marks the form permanent and releases it for its own submission. |
| HTML update | Region and revision tracking on the browser, current region from the server | Turbo page refresh and morphing with stable HTML IDs |
| Publication | Process-local `StateFlow`, SSE invalidation, then authorized GET | Action Cable async broadcast on a signed stream, Turbo refresh, then standard GET |
| Reconnect | Reauthorize and request current state upon a fresh SSE subscription | Reconnect and subscribe to the signed stream; the process-local broadcast adapter has no catch-up log |
| Mutation | Typed action over HTTP with ordinary PRG and enhanced patch output | Rails controller over HTTP, CSRF middleware and ordinary PRG, followed by Turbo Drive if enabled |

Inspect the [Woge interaction policy](../../docs/guides/data-freshness.md), the [Turbo refresh guide](https://turbo.hotwired.dev/handbook/page_refreshes)
and the [Action Cable adapter rules](https://guides.rubyonrails.org/action_cable_overview.html#subscription-adapter).
A matched transport swap is outside this experiment. Connection behavior follows the configured
subscription, replay and DOM policies. Either transport can support different policies.

## Comparator selection

- **Hotwire** fits an existing Rails team that wants to preserve server templates, ordinary forms
  and a useful native path while adding live refresh. Turbo Drive and Streams supply HTML navigation
  and refresh, Action Cable supplies subscriptions, and Stimulus can attach targeted behavior when
  desired. A signed stream identifies a stream subscription. In a secured Rails app, establish the
  connection identity and channel permissions too. This independently implemented fixture uses
  Turbo and Action Cable. Its core journeys require no Stimulus controllers. [Turbo handbook](https://turbo.hotwired.dev/handbook/introduction) · [Turbo Streams](https://turbo.hotwired.dev/handbook/streams) · [Stimulus lifecycle](https://stimulus.hotwired.dev/reference/lifecycle-callbacks) · [Action Cable](https://guides.rubyonrails.org/action_cable_overview.html)
- **Phoenix LiveView** fits an Elixir team with dense stateful interactions that benefit from a
  server-owned view process, assigns and incremental diffs. Compare process footprint and remount
  against persisted data, authorize on mount and before sensitive events, and examine form recovery
  separately from mutation recovery. LiveView documents recovery for forms with `phx-change` and
  IDs. Initial HTML is available. A useful no-JavaScript mutation path depends on the application
  implementing ordinary forms and actions. [LiveView lifecycle](https://phoenix-live-view.hexdocs.pm/Phoenix.LiveView.html) · [Form recovery](https://phoenix-live-view.hexdocs.pm/form-bindings.html#recovery-following-crashes-or-disconnects) · [Security model](https://phoenix-live-view.hexdocs.pm/security-model.html)
- **Laravel Livewire** fits a Laravel team keeping Blade, data access and validation in its existing
  PHP stack. The runtime hydrates a component snapshot on subsequent requests and uses morphing to
  apply returned HTML. Treat submitted properties as untrusted and reapply authorization. Livewire can
  consume Laravel Echo broadcasts if the application needs external events. A native form path and
  recovery from a lost response depend on the application design. [Hydration](https://livewire.laravel.com/docs/4.x/hydration) · [Security](https://livewire.laravel.com/docs/4.x/security) · [Events](https://livewire.laravel.com/docs/4.x/events)

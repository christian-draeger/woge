# When to try Woge

Woge is a fit to evaluate when a Kotlin server owns the page, validates form submissions and sends
HTML updates. Start with a small workflow that works through links and forms, then decide where
enhancement helps. If an existing template and fragment setup already meets the same requirements
with less change, keep it. For a screen dominated by offline work or rich client-owned editing,
evaluate a client-owned approach against that workflow.
[MVP scope](../mvp-boundary.md) · [What is tested on each host](../architecture/server-adapter-parity.md) · [Content and browser-owned work](product-boundaries.md)

The first product review was scoped on **2026-11-24** and evaluated on **2026-11-25**. The
[claim register](../performance/claim-evidence.md) records tested versions, comparisons and
revalidation triggers. [Product evidence roadmap](https://github.com/christian-draeger/woge/issues/283)

## Pick an installation for the evaluation

| Path | What you can reproduce |
| --- | --- |
| Source checkout | The maintained applications, adapter tests and documented APIs at the commit you select. APIs can be newer than the packaged alpha. |
| Published alpha | GitHub Packages carries `0.1.0-alpha.1` with authenticated downloads. The public Todo demo image has its own version and digest. |
| Public Maven Central snapshot | `0.1.0-SNAPSHOT` is an anonymous, moving main build. Record the resolved snapshot and source commit for a result. |
| Fixed pilot kit | The `0.1.0-beta.1` kit uses commit `7bf1e3e` and a local Maven repository. The Spring scaffold and PetClinic sample built from the separately supplied ZIP. The beta release decision and registry publication are pending. |

[Package guide](../guides/packages.md) · [Todo demo digest](../guides/todo-container.md#run-the-published-demo) ·
[Pinned pilot candidate and verification](https://github.com/christian-draeger/woge/issues/50)

### Capabilities by maturity

| Capability | Current evidence and remaining condition |
| --- | --- |
| Published Todo demo | Its pinned OCI image exercises native and enhanced forms, filtering and two-tab updates on `0.1.0-alpha.1`. Use its own version and support notes. [Demo recipe](../guides/todo-container.md#run-the-published-demo) |
| Main source and snapshots | MVC, WebFlux and Ktor share the maintained reference and adapter checks. The WebFlux multipart timeout still recurs in Linux CI. [Host coverage](../architecture/server-adapter-parity.md) · [Multipart investigation](https://github.com/christian-draeger/woge/issues/278) |
| Experimental | Native declarative updates have a browser-specific support boundary. Micronaut and Quarkus running-example evidence is scoped to the reported spike versions and workflows. [Browser support](../guides/browser-support.md#experimental-native-dpu) · [Micronaut spike](../../spikes/micronaut-host/README.md) · [Quarkus spike](../../spikes/quarkus-host/README.md) |
| Unverified for a public beta or 1.0 promise | Independent onboarding, manual UI and assistive-technology results, and a supported rolling-compatibility promise remain release criteria. [Beta decision](https://github.com/christian-draeger/woge/issues/50) · [1.0 compatibility](https://github.com/christian-draeger/woge/issues/62) |

## Server-owned forms and live lists

**Try a pilot.** This is the closest fit for a Kotlin team building CRUD or an operations screen with
ordinary HTML, validation and small updates to a list or count. Use Spring WebFlux with Thymeleaf
and htmx, or Ktor with `kotlinx.html` and htmx, as the direct comparison on the same host.

The frozen Todo comparison verifies shared native submissions, validation, filtering and enhanced
two-tab updates for both approaches within its scope. At the tested load Woge had higher enhanced
toggle HTTP p95 and board-render allocation on both hosts. The descriptive visible-interaction
sample gives mixed results. This is good evidence for the shared fixture behavior, with
limited confidence for latency or operational decisions about a different deployment.
[Observed behavior and costs](../performance/claim-evidence.md#frozen-incremental-todo-comparison)

The pinned pilot passed the optional note, field rename and validation changes. Two fresh Codex
sessions diagnosed and repaired the same seeded rename with terminal and local MCP feedback. The
independently packaged KTE/htmx reference also passed the scoped Add, validation and live-refresh
journeys. In the separate Next.js/KTE study, the Woge fixture kept its dirty keyed input after
Add. That journey required clearing the input, so the Woge arm supplied parity findings only.
The deterministic change-safety corpus identifies which incomplete Todo edits each compiler and
browser check detects. Comparative feature effort and independent human usability remain open.
[Cross-application change-safety checks](../../spikes/todo-change-safety/README.md) · [Todo change evidence](../ai-dx/results/2026-11-24-beta-todo-agent.md) ·
[Matched feedback trial](../ai-dx/results/2026-11-25-beta-todo-mcp.md) ·
[KTE/htmx fixture](../../spikes/todo-kte-htmx/README.md) ·
[Next.js/KTE deployment study](../../spikes/todo-next/README.md)

## Add one feature to an existing secured Spring application

**Narrow the trial to one MVC feature.** If a Spring MVC team wants to retain its login, domain
rules and services, compare adding the feature with Woge to adding it in the application's
existing Thymeleaf views. The PetClinic reference reuses the real application session, routes,
domain and persistence for owner search, pet details and visit booking. Scoped browser and HTTP
checks cover native and enhanced booking, failed validation and CSRF, and one durable visit
through both views. The migration work changes application routes, Kotlin page and action code
and adapter wiring for the selected feature.

The two-node fixture also checks shared sessions, durable writes with explicit idempotency and
peer refresh through TLS and HTTP/2. The tested application and topology establish a path to try
for one MVC feature. Comparing the migration effort with the original Thymeleaf route, rolling
released versions and trying it in an independent application remain separate decisions.
[PetClinic run and scope](../../examples/petclinic-woge/README.md) ·
[Adoption study](https://github.com/christian-draeger/woge/issues/286) ·
[Persistent multi-instance study](https://github.com/christian-draeger/woge/issues/289)

## Show a useful page while slow sections load

**Try the reference, defer performance claims.** This is relevant to project dashboards with
independent data loads and an HTML-only path. Compare Woge's reference with an ordinary
Spring or Ktor template page that exposes the same complete view and separately fetches delayed
HTML fragments.

The reference shows a useful shell, independently completed regions and a native link to the
complete page. The same browser journeys and adapter contract run on MVC, WebFlux and Ktor. In
the pinned three-host study, every one of 2,304 scheduled journeys met the declared 750 ms budget
at 8 and 24 arrivals per second. Each disconnect and recovery control drained its connections.
Browser samples measured first paint and first applied patch. A matched fragment baseline,
complete-visible timing and saturation remain unknown. The setup adds host binding, a region
budget and transport checks for the target deployment.
[Runnable reference](../../examples/reference-application/README.md#follow-the-web-flow) ·
[Adapter and cancellation coverage](../architecture/server-adapter-parity.md) ·
[Bounded study and raw data](../performance/reference-project-deferred-2026-11-25.md)

## Acceptance bars for a product recommendation

Agree on acceptable time, cost and latency differences before the comparative trial, using the
team's delivery constraints and pilot variation. A positive decision requires the relevant
observable behavior and resource bounds to hold. Use the same candidate, comparison workload
and checks throughout. [Measurement contract](../performance/comparison-contract.md)

| Workflow | Behavior bar | Adoption, operations and independent-use bar |
| --- | --- | --- |
| Live list | Native and enhanced submissions reach the same state. Invalid text and focus survive. Two tabs converge, preserving the draft and preventing duplicate commits. | Ship a feature and rename with each baseline under the same checks. Declare the team's change and browser/response budgets from an initial run and record files, dependencies and iterations. A pilot completes the public guide's tasks and records any help. |
| Secured MVC feature | Existing views and login continue to work, a rejected write changes no data and both views show one successful write. | Keep the existing login, persistence and service wiring. Revert the bounded feature while the original path still works. Check the same public-origin and session rules. A Spring pilot exercises a real feature against its existing views and records adoption effort. |
| Deferred page | Users see useful shell content first, the completed data in both modes and clean cancellation at the declared boundary. | Match a native page and a progressively loaded fragment baseline. Require correct results and cleanup at the declared load and resource tier. Choose a complete-visible budget from user needs before timing. An independent participant reproduces the native and enhanced paths. |

The evidence supports proceeding with a scoped live-list pilot, narrowing the Spring trial to
one secured MVC feature and deferring any comparative performance recommendation for the
deferred page. The historical Todo rendering and p95 gaps remain listed in the claim register.
Human and change-effort bars are unverified across the three workflows. Review again at
compatibility releases as matching evidence changes.

Near-term adoption owns the [package path](https://github.com/christian-draeger/woge/issues/285),
[Quickstart usability](https://github.com/christian-draeger/woge/issues/49) and
[design partners](https://github.com/christian-draeger/woge/issues/50). Core stabilization owns
[independent review and compatibility](https://github.com/christian-draeger/woge/issues/62) and
[render/action profiling](https://github.com/christian-draeger/woge/issues/294). Later studies own
[comparative change safety](https://github.com/christian-draeger/woge/issues/291),
[human/agent comparisons](https://github.com/christian-draeger/woge/issues/292) and
[larger client-owned workflows](https://github.com/christian-draeger/woge/issues/296). The
[comparison issue](https://github.com/christian-draeger/woge/issues/290) owns equivalent full-stack
behavior fixes. [The performance study](https://github.com/christian-draeger/woge/issues/293)
owns matching deferred behavior and complete-visible timing.

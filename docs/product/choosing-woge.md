# When to try Woge

Woge is a fit to evaluate when a Kotlin server owns the page, validates form submissions and sends
HTML updates. Start with a small workflow that works through links and forms, then decide where
enhancement helps. If an existing template and fragment setup already meets the same requirements
with less change, keep it. For a screen dominated by offline work or rich client-owned editing,
evaluate a client-owned approach against that workflow.
[MVP scope](../mvp-boundary.md) · [What is tested on each host](../architecture/server-adapter-parity.md) · [Content and browser-owned work](product-boundaries.md)

This first product evidence review is dated **2026-11-24**. Confidence below describes the tested
fixture and use case. The [claim register](../performance/claim-evidence.md) supplies scoped wording
and revalidation triggers for comparisons. [Product evidence roadmap](https://github.com/christian-draeger/woge/issues/283)

## Pick an installation for the evaluation

| Path | What you can reproduce |
| --- | --- |
| Source checkout | The maintained applications, adapter tests and documented APIs at the commit you select. APIs can be newer than the packaged alpha. |
| Published alpha | GitHub Packages carries `0.1.0-alpha.1` with authenticated downloads. The public Todo demo image has its own version and digest. |
| Public Maven Central snapshot | `0.1.0-SNAPSHOT` is an anonymous, moving main build. Record the resolved snapshot and source commit for a result. |
| Fixed pilot kit | The `0.1.0-beta.1` kit uses commit `7bf1e3e` and a local Maven repository. The Spring scaffold and PetClinic sample built from the separately supplied ZIP. The beta release decision and registry publication are pending. |

[Package guide](../guides/packages.md) · [Todo demo digest](../guides/todo-container.md#run-the-published-demo) ·
[Pinned pilot candidate and verification](https://github.com/christian-draeger/woge/issues/50)

Core APIs and host integrations are pre-1.0. The bounded candidate multipart checks passed.
The historical [WebFlux timeout](https://github.com/christian-draeger/woge/issues/278) remains open. For the deferred workflow below use the documented
cross-browser enhancement and complete-page fallback. The opt-in native declarative update path
has a separate experimental browser boundary. Micronaut remains a spike and Quarkus needs adapter
evidence. [Compatibility policy](../guides/compatibility-and-upgrades.md) ·
[Browser support](../guides/browser-support.md#experimental-native-dpu) ·
[Micronaut evaluation](https://github.com/christian-draeger/woge/issues/302) ·
[Quarkus adapter](https://github.com/christian-draeger/woge/issues/298)

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

The pinned pilot candidate also passed the optional note, field rename and validation change
tasks. An independent Codex session repeated the field rename. These results cover candidate
change safety in those tasks. Comparative change effort, another model family, the unaided human
control and real-project usability remain open.
[Todo change evidence](../ai-dx/results/2026-11-24-beta-todo-agent.md) ·
[Repeat and pilot status](https://github.com/christian-draeger/woge/issues/50)

## Add one feature to an existing secured Spring application

**Narrow the trial to one MVC feature.** If a Spring MVC team wants to retain its login, domain
rules and services, compare adding the feature with Woge to adding it in the application's
existing Thymeleaf views. The PetClinic reference reuses the real application session, routes,
domain and persistence for owner search, pet details and native visit booking. The Spring
integration test checks login, rejects missing CSRF, exercises validation and confirms the
submitted visit through both views.

Confidence is limited to this single-instance MVC fixture and its tested native path. Adoption
effort, the enhanced version of the journey and durable multi-instance behavior still need
matching evidence. Keep the existing implementation for a broader migration decision until
those requirements have been checked against the application's own constraints.
[PetClinic run and scope](../../examples/petclinic-woge/README.md) ·
[Adoption study](https://github.com/christian-draeger/woge/issues/286) ·
[Persistent multi-instance study](https://github.com/christian-draeger/woge/issues/289)

## Show a useful page while slow sections load

**Try the reference, defer performance claims.** This is relevant to project dashboards with
independent data loads and an HTML-only path. Compare Woge's reference with an ordinary
Spring or Ktor template page that exposes the same complete view and separately fetches delayed
HTML fragments.

The reference shows a useful shell, independently completed regions and a native link to the
complete page. The same browser journeys and adapter contract run on MVC, WebFlux and Ktor.
This establishes useful fixture behavior across those hosts. Proxy, idle disconnect and load
behavior require the documented host checks and equivalent end-to-end evidence for the deployment
under consideration. [Runnable reference](../../examples/reference-application/README.md#follow-the-web-flow) ·
[Adapter and cancellation coverage](../architecture/server-adapter-parity.md) ·
[Comparative performance study](https://github.com/christian-draeger/woge/issues/293)

## Criteria for the next decision

Agree on acceptable time, cost and latency differences before the comparative trial, using the
team's delivery constraints and pilot variation. A positive decision requires the relevant
observable behavior and resource bounds to hold. Use the same candidate, comparison workload
and checks throughout. [Measurement contract](../performance/comparison-contract.md)

| Workflow | Behavior bar | Change, operation and usability evidence still needed |
| --- | --- | --- |
| Live list | Native and enhanced submissions reach the same state. Invalid text and focus survive. Two tabs converge, preserving the draft and preventing duplicate commits. | Compare a feature and a rename from the same baseline. Record files, dependencies, iterations and browser bytes. Have a pilot complete the form/page/action tasks with the public guide and record any help. |
| Secured MVC feature | Existing views and login continue to work, a rejected write changes no data and both views show one successful write. | Record the migration and rollback footprint and run the relevant session/proxy checks. A Spring pilot exercises a real feature and compares effort with its existing views. |
| Deferred page | Users see useful shell content first, the completed data in both modes and clean cancellation at the declared boundary. | Compare change effort and visible outcomes with the template/fragment baseline. Under the declared proxy, network and load limits, record time to shell and completion, cleanup and resource use. Have a participant reproduce the complete and deferred journeys. |

Near-term adoption owns the [package path](https://github.com/christian-draeger/woge/issues/285),
[Quickstart usability](https://github.com/christian-draeger/woge/issues/49) and
[design partners](https://github.com/christian-draeger/woge/issues/50). Core stabilization owns
[independent review and compatibility](https://github.com/christian-draeger/woge/issues/62) and
[render/action profiling](https://github.com/christian-draeger/woge/issues/294). Later studies own
[comparative change safety](https://github.com/christian-draeger/woge/issues/291),
[human/agent comparisons](https://github.com/christian-draeger/woge/issues/292) and
[larger client-owned workflows](https://github.com/christian-draeger/woge/issues/296). Review the
recommendations again at compatibility releases as evidence changes.

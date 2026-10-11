# Spike lifecycle

Spikes are executable evidence for one bounded question. They are not production modules, supported APIs, or an alternative implementation of Woge.

The maintained multi-host reference application is the integration point for accepted decisions. Do not combine the M0 investigations into a framework-like mega-spike.

## States

| State | Meaning |
| --- | --- |
| Active | The question is open and the spike may change to gather evidence |
| Frozen | Its ADR is accepted; change only broken reproduction, security-sensitive evidence, or an invalidated measurement |
| Superseded | Production code and tests cover the decision; the spike no longer runs in normal CI |
| Retired | Prototype code was removed after durable evidence links and all valuable tests were migrated |

Changing an accepted architectural conclusion requires the normal [ADR lifecycle](../docs/adr/README.md), not an edit that makes the old spike appear to have reached a different result.

## M0 inventory

Frozen spikes stay in the repository as small, executable evidence. Retired spikes were removed after production code and tests covered their decision; their links point to the last commit that contained them.

| State | Spike | Decision | Production successor |
| --- | --- | --- | --- |
| Retired | [`spring-html-htmx-baseline`](https://github.com/christian-draeger/woge/tree/8f9f92e22fab2ecaa1e7a205466949f2bea11a1b/spikes/spring-html-htmx-baseline) | Reference journey and host comparison in ADRs [0004](../docs/adr/0004-project-operations-reference-application.md) and [0005](../docs/adr/0005-server-host-use-case-ports.md) | Multi-host slice [#24](https://github.com/christian-draeger/woge/issues/24) and adapter TCK [#65](https://github.com/christian-draeger/woge/issues/65) |
| Frozen | [`typed-reference-model`](typed-reference-model/) | Typed web references in ADR [0011](../docs/adr/0011-typed-web-references.md) | Identity and KSP descriptors [#25](https://github.com/christian-draeger/woge/issues/25), [#26](https://github.com/christian-draeger/woge/issues/26), [#27](https://github.com/christian-draeger/woge/issues/27) and [#28](https://github.com/christian-draeger/woge/issues/28) |
| Retired | [`html-writer-strategy`](https://github.com/christian-draeger/woge/tree/8f9f92e22fab2ecaa1e7a205466949f2bea11a1b/spikes/html-writer-strategy) | Streaming writer and `kotlinx.html` interop in ADR [0012](../docs/adr/0012-html-writer-and-kotlinx-interop.md) | Safe rendering [#16](https://github.com/christian-draeger/woge/issues/16) and sinks [#17](https://github.com/christian-draeger/woge/issues/17) |
| Frozen | [`patch-framing`](patch-framing/) | Length-prefixed framing in ADR [0013](../docs/adr/0013-length-prefixed-patch-framing.md) | Fallback encoding [#20](https://github.com/christian-draeger/woge/issues/20) and fuzzing [#41](https://github.com/christian-draeger/woge/issues/41) |
| Frozen | [`fallback-patch-runtime`](fallback-patch-runtime/) | Small owned browser runtime in ADR [0014](../docs/adr/0014-small-owned-fallback-patch-runtime.md) | Runtime [#21](https://github.com/christian-draeger/woge/issues/21) and browser conformance [#39](https://github.com/christian-draeger/woge/issues/39) |
| Frozen | [`native-dpu`](native-dpu/) | Experimental native ceiling in ADR [0015](../docs/adr/0015-limit-native-dpu-to-initial-document-optimization.md) | Optional encoder [#40](https://github.com/christian-draeger/woge/issues/40) |
| Retired | [`css-authoring`](https://github.com/christian-draeger/woge/tree/8f9f92e22fab2ecaa1e7a205466949f2bea11a1b/spikes/css-authoring) | Standards-native CSS in ADR [0016](../docs/adr/0016-standards-native-css-authoring.md) | Asset/style contract [#78](https://github.com/christian-draeger/woge/issues/78) |
| Frozen | [`tailwind-kotlin`](tailwind-kotlin/) | Optional Tailwind adapter in ADR [0017](../docs/adr/0017-optional-tailwind-build-adapter.md) | Supported build adapter [#79](https://github.com/christian-draeger/woge/issues/79) |
| Frozen | [`component-distribution`](component-distribution/) | Headless plus source-owned model in ADR [0018](../docs/adr/0018-hybrid-headless-and-source-owned-components.md) | Headless foundation [#80](https://github.com/christian-draeger/woge/issues/80) and registry [#81](https://github.com/christian-draeger/woge/issues/81) |

## Post-M0 evidence

| State | Spike | Decision | Production successor |
| --- | --- | --- | --- |
| Retired | [`spring-boot-reload-topology`](https://github.com/christian-draeger/woge/tree/8f9f92e22fab2ecaa1e7a205466949f2bea11a1b/spikes/spring-boot-reload-topology) | Woge-owned orchestration, Spring DevTools trigger adapter, SSE lifecycle channel and no initial proxy in ADR [0041](../docs/adr/0041-orchestrator-owned-spring-reload-and-sse-channel.md) | Orchestrator [#147](https://github.com/christian-draeger/woge/issues/147), Spring restart [#144](https://github.com/christian-draeger/woge/issues/144) and browser channel [#145](https://github.com/christian-draeger/woge/issues/145) |
| Frozen | [`vite-frontend`](vite-frontend/) | Optional, directly invoked Vite adapter; Kotlin sources stay outside Vite in ADR [0070](../docs/adr/0070-optional-direct-vite-frontend-adapter.md) | Vite adapter [#205](https://github.com/christian-draeger/woge/issues/205) |

| Frozen | [`local-island-target`](local-island-target/) | Kotlin/JS for opt-in DOM islands in ADR [0093](../docs/adr/0093-kotlin-js-local-islands.md) | Lazy lifecycle [#54](https://github.com/christian-draeger/woge/issues/54), shared validation [#55](https://github.com/christian-draeger/woge/issues/55), local signals [#56](https://github.com/christian-draeger/woge/issues/56) |

## Pre-1.0 audit (2026-10-11)

The audit retains the eight remaining investigations as **Frozen**, not Active. Having a production
successor does not mean every comparison against a rejected alternative belongs in production CI.
No prototype is removed in this audit; historical measurements, negative fixtures and relative ADR
links therefore remain intact. The four already Retired entries above retain their immutable links.

| Frozen evidence | Maintained successor and durable checks | Reason to retain executable evidence |
| --- | --- | --- |
| Typed references | `woge-host-spi` identity tests, `woge-ksp` route/action/region diagnostics and `examples/m1-api-corpus` negative compiler fixtures | The old nominal component-owner API differs from the current page-region API. Keep the original compiler diagnostics reproducible; do not copy the obsolete API into production. |
| Patch framing | `woge-protocol` codec, golden-wire and seeded fuzz tests; `PatchStreamCompressionTest` carries the gzip/one-byte case | The rejected multipart codec and its overhead comparison have no production counterpart. Keep comparison code, but never use it as a second supported codec. |
| Fallback runtime | `client/woge-fallback-client` unit and three-engine browser contracts; shared hostile-markup fixtures and protocol tests | The small classic-script implementation records the original size and library comparison. It is not a published client or a source for current fixes. |
| Native DPU | `woge-server-runtime` encoder tests; original-document conformance on MVC, WebFlux and Ktor from #230 | Nested/parent/shadow scope, repeated markers, active-parser execution and the experimental streaming API remain external platform evidence. The production path intentionally supports a narrower passive range replacement. |
| Tailwind | Gradle Tailwind build adapter and `testTailwindExample` in the parallel example CI job | The standalone/npm parity measurement and historical watcher/output hashes describe exact external tool versions. Keep them reproducible separately from supported application builds. |
| Component distribution | `woge-ui-headless`, `woge-component-registry`, compiled recipes and component browser contracts | Styled-binary versus source-only versus hybrid artifact measurements are a rejected-alternative comparison, not three production component systems. |
| Vite | `woge-vite` tests, `ViteDevServerTest` and `testViteExample` in the example CI job | The OpenSavvy plugin probe and original HMR timings explain why direct invocation was chosen. Do not bring that plugin into the maintained build. |
| Local-island target | `woge-core` island declarations, the maintained Kotlin/JS example and client island lifecycle tests | Wasm/dual payload, fallback and source-map comparisons are intentionally outside the supported Kotlin/JS island target. |

Production security, protocol and browser contracts belong to the successors, not these prototypes.
The added gzip regression exercises the current wire format rather than importing the spike codec.
Experimental original-document conformance remains separate from the normal three-engine fallback
gate. Its new CI wiring must pass before any related historical workflow is disabled.

The remaining spike workflows stay path-scoped: they reproduce the external comparisons above
when their own evidence changes, not on ordinary product changes. No workflow is disabled and no
fixture is deleted on the strength of a local check alone. A later **Superseded** decision must name
the green production CI run and account for the remaining comparison-specific cases first.

### Generated-file cleanup

Generated files are disposable, but evidence reports and lockfiles are not. First inspect the ignored
outputs for one exact spike, for example:

```sh
git status --ignored --short -- spikes/patch-framing
git clean -ndX -- spikes/patch-framing
```

For Gradle outputs, prefer `./gradlew -p spikes/patch-framing clean`. This does not remove installed
dependencies or all tool caches. If those also need removal, review the dry-run list above and then
use `git clean -fdX -- spikes/patch-framing` for that exact spike only. This deletes **all ignored
files in that spike**, including local measurement output; preserve anything needed first. Repeat
with one explicitly named spike, never the repository or a wildcard. Do not clear shared Gradle/npm
caches or the shared Playwright browser installation as part of spike cleanup.

Downloaded tools, local wrappers, `node_modules`, `build`, `.gradle` and `.kotlin` stay ignored.
`git ls-files -- spikes` is the source inventory; installed tools must not be added to it.

## Repository rules

- Production modules, adapters, integrations and maintained examples must never depend on a spike project or import a spike package.
- Each spike keeps its own build and locked external dependencies. It must not enter the root production Gradle graph.
- Spike workflows remain path-scoped. Normal product changes must not rebuild every historical experiment.
- Generated output, `.gradle`, `.kotlin`, `build`, `node_modules`, downloaded browsers and local wrapper copies stay untracked.
- Evidence names the tested tool/browser versions and links its accepted ADR. The ADR and canonical architecture documentation own the current contract.
- A frozen spike receives no convenience features. A necessary repair must preserve the original question and update its evidence when results change.
- New investigations get a time box, acceptance question, evidence report, recommendation and explicit production follow-up.

## Superseding or retiring a spike

Before changing a spike to **Superseded** or **Retired**:

1. Link the production module, maintained example and backlog issue that replace it.
2. Move durable behavior, negative compiler, malformed-input, accessibility, browser and security cases into production CI.
3. Demonstrate that production code has no dependency on the spike.
4. Preserve reproducible evidence in the repository or update ADR links to an immutable tag/commit permalink.
5. Disable the path-scoped spike workflow only after equivalent production checks pass.
6. Update the inventory state in this file in the same pull request.

The pre-1.0 audit is tracked by [#107](https://github.com/christian-draeger/woge/issues/107). Deleting prototype code is optional: retained evidence has low cost, while losing a useful boundary test has high cost.

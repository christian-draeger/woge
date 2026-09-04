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

All M0 spikes are **Frozen**. Their source remains small, independently executable evidence while M1–M4 build the supported implementation.

| Spike | Decision | Production successor |
| --- | --- | --- |
| [`spring-html-htmx-baseline`](spring-html-htmx-baseline/) | Reference journey and host comparison in ADRs [0004](../docs/adr/0004-project-operations-reference-application.md) and [0005](../docs/adr/0005-server-host-use-case-ports.md) | Multi-host slice [#24](https://github.com/christian-draeger/woge/issues/24) and adapter TCK [#65](https://github.com/christian-draeger/woge/issues/65) |
| [`typed-reference-model`](typed-reference-model/) | Typed web references in ADR [0011](../docs/adr/0011-typed-web-references.md) | Identity and KSP descriptors [#25](https://github.com/christian-draeger/woge/issues/25), [#26](https://github.com/christian-draeger/woge/issues/26), [#27](https://github.com/christian-draeger/woge/issues/27) and [#28](https://github.com/christian-draeger/woge/issues/28) |
| [`html-writer-strategy`](html-writer-strategy/) | Streaming writer and `kotlinx.html` interop in ADR [0012](../docs/adr/0012-html-writer-and-kotlinx-interop.md) | Safe rendering [#16](https://github.com/christian-draeger/woge/issues/16) and sinks [#17](https://github.com/christian-draeger/woge/issues/17) |
| [`patch-framing`](patch-framing/) | Length-prefixed framing in ADR [0013](../docs/adr/0013-length-prefixed-patch-framing.md) | Fallback encoding [#20](https://github.com/christian-draeger/woge/issues/20) and fuzzing [#41](https://github.com/christian-draeger/woge/issues/41) |
| [`fallback-patch-runtime`](fallback-patch-runtime/) | Small owned browser runtime in ADR [0014](../docs/adr/0014-small-owned-fallback-patch-runtime.md) | Runtime [#21](https://github.com/christian-draeger/woge/issues/21) and browser conformance [#39](https://github.com/christian-draeger/woge/issues/39) |
| [`native-dpu`](native-dpu/) | Experimental native ceiling in ADR [0015](../docs/adr/0015-limit-native-dpu-to-initial-document-optimization.md) | Optional encoder [#40](https://github.com/christian-draeger/woge/issues/40) |
| [`css-authoring`](css-authoring/) | Standards-native CSS in ADR [0016](../docs/adr/0016-standards-native-css-authoring.md) | Asset/style contract [#78](https://github.com/christian-draeger/woge/issues/78) |
| [`tailwind-kotlin`](tailwind-kotlin/) | Optional Tailwind adapter in ADR [0017](../docs/adr/0017-optional-tailwind-build-adapter.md) | Supported build adapter [#79](https://github.com/christian-draeger/woge/issues/79) |
| [`component-distribution`](component-distribution/) | Headless plus source-owned model in ADR [0018](../docs/adr/0018-hybrid-headless-and-source-owned-components.md) | Headless foundation [#80](https://github.com/christian-draeger/woge/issues/80) and registry [#81](https://github.com/christian-draeger/woge/issues/81) |

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

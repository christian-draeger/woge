# Architecture decision records

ADRs preserve why Woge made a consequential choice, which alternatives were considered and what the choice costs. They complement tutorials and API reference; they do not replace them.

## Lifecycle

1. Copy [`0000-template.md`](0000-template.md) to the next free four-digit number and a short lowercase slug.
2. Start with `Proposed`. Link the issue or pull request that supplies evidence.
3. Record concrete alternatives and consequences before changing the status.
4. Use one of: `Proposed`, `Accepted`, `Rejected`, `Deprecated`, or `Superseded`.
5. Never rewrite an accepted decision to hide history. Add a new ADR and mark the old one `Superseded`, with links in both directions.
6. Add every ADR to the index below.

An ADR is required for changes to public APIs, protocols, module boundaries, security defaults, supported platforms or compatibility promises. A narrow implementation detail with no effect on those contracts is exempt.

## Validation

Run:

```shell
./scripts/validate-adrs.sh
```

The check validates filenames, required sections, metadata, duplicate numbers, index entries and local Markdown links.

## Index

| ADR | Status | Decision |
| --- | --- | --- |
| [0001](0001-web-native-product-boundary.md) | Accepted | Keep Woge web-native and progressively enhanced |
| [0002](0002-framework-neutral-host-boundary.md) | Accepted | Put server frameworks behind Woge host adapters |
| [0003](0003-web-first-documentation-and-ai-dx.md) | Accepted | Use web-first documentation and compiler-guided AI DX |
| [0004](0004-project-operations-reference-application.md) | Accepted | Use one project operations dashboard as the reference application |
| [0005](0005-server-host-use-case-ports.md) | Accepted | Model server adapters as Woge use-case exchanges |
| [0006](0006-initial-module-boundaries.md) | Superseded | Enforce a small inward-pointing initial module graph |
| [0007](0007-browser-support-and-progressive-enhancement.md) | Accepted | Guarantee an HTML baseline before browser enhancement |
| [0008](0008-security-trust-boundaries.md) | Accepted | Make native and enhanced paths share secure boundaries |
| [0009](0009-frontend-extension-contract.md) | Accepted | Layer frontend extensions over semantic server HTML |
| [0010](0010-identity-epochs-and-revisions.md) | Accepted | Scope rendered identity and revisions to a page epoch |
| [0011](0011-typed-web-references.md) | Accepted | Generate distinct typed descriptors for web references |
| [0012](0012-html-writer-and-kotlinx-interop.md) | Accepted | Own a minimal streaming HTML writer with kotlinx.html interop |
| [0013](0013-length-prefixed-patch-framing.md) | Accepted | Use explicit length-prefixed patch frames |
| [0014](0014-small-owned-fallback-patch-runtime.md) | Accepted | Own a small protocol-specific fallback patch runtime |
| [0015](0015-limit-native-dpu-to-initial-document-optimization.md) | Accepted | Limit native DPU to an opt-in initial-document optimization |
| [0016](0016-standards-native-css-authoring.md) | Accepted | Keep CSS standards-native with optional build-time scoping |
| [0017](0017-optional-tailwind-build-adapter.md) | Accepted | Integrate Tailwind through an optional build adapter |
| [0018](0018-hybrid-headless-and-source-owned-components.md) | Accepted | Combine binary headless primitives with source-owned component recipes |
| [0019](0019-materialized-m1-module-boundaries.md) | Accepted | Materialize the M1 module and consumer boundaries |
| [0020](0020-context-specific-html-values.md) | Accepted | Separate HTML value contexts and make active contexts explicit |
| [0021](0021-synchronous-bounded-html-sinks.md) | Accepted | Keep HTML sinks synchronous, bounded and transport-neutral |

ADRs 0001–0018 are the complete M0 decision set. ADRs beginning with 0019 record implementation-era
decisions and supersessions. The [MVP boundary](../mvp-boundary.md) is the canonical short synthesis;
executable spikes are supporting evidence and do not override an accepted ADR. Issue
[#12](https://github.com/christian-draeger/woge/issues/12) is exempt from a separate ADR because it
indexes and assembles these decisions rather than adding one.

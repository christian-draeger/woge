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
| [0022](0022-page-host-spi-contract.md) | Accepted | Use one narrow typed page boundary with policy-checked outcomes |
| [0023](0023-minimal-replace-patch-ir.md) | Accepted | Keep Replace Patch IR semantic, explicit and closed |
| [0024](0024-strict-bounded-patch-stream-codec.md) | Accepted | Validate bounded canonical patch frames before exposure |
| [0025](0025-browser-replace-runtime-and-lifecycle.md) | Accepted | Apply Replace patches through a page-local DOM registry |
| [0026](0026-structured-deferred-region-execution.md) | Accepted | Execute deferred regions as bounded request children |
| [0027](0027-fetch-deferred-patches-after-html-shell.md) | Accepted | Fetch deferred patches after the HTML shell |
| [0028](0028-functional-spring-webflux-adapter.md) | Accepted | Adapt Woge through functional Spring WebFlux handlers |
| [0029](0029-neutral-spring-boot-starter-and-explicit-adapter-selection.md) | Accepted | Keep the Spring Boot starter neutral and adapter selection explicit |
| [0030](0030-materialize-css-and-head-asset-boundaries.md) | Accepted | Materialize CSS and head asset boundaries without a styling runtime |
| [0031](0031-root-build-spring-boot-quickstart-consumer.md) | Accepted | Keep the canonical Spring Boot quickstart as a separated root-build consumer |
| [0032](0032-async-servlet-spring-mvc-adapter.md) | Accepted | Stream Spring MVC responses through adapter-owned Servlet async handlers |
| [0033](0033-suspending-ktor-adapter.md) | Accepted | Bind Woge to idiomatic suspending Ktor routes |
| [0034](0034-generate-html-element-wrappers-from-webref.md) | Accepted | Generate HTML element wrappers from pinned Webref data |
| [0035](0035-framework-neutral-semantic-observation-port.md) | Accepted | Use a framework-neutral semantic observation port |
| [0036](0036-dual-fallback-client-distribution.md) | Accepted | Distribute one fallback client through npm and a JVM asset adapter |
| [0037](0037-compile-verified-m1-corpus.md) | Accepted | Verify the public M1 surface with compiler fixtures and a framework index |
| [0038](0038-build-independent-development-lifecycle.md) | Accepted | Share one build-independent development lifecycle across tool adapters |
| [0039](0039-versioned-external-spring-boot-application-scaffold.md) | Accepted | Maintain one versioned external Spring Boot application scaffold |
| [0040](0040-generate-application-agent-guidance-from-public-metadata.md) | Accepted | Generate application agent guidance from public framework metadata |
| [0041](0041-orchestrator-owned-spring-reload-and-sse-channel.md) | Accepted | Let Woge own Spring development orchestration and use SSE for browser lifecycle events |
| [0042](0042-single-actor-development-orchestrator.md) | Accepted | Run the development orchestrator as one single-threaded coordinator |
| [0043](0043-spring-dev-host-uses-log-marker-and-trigger-file.md) | Accepted | Drive the Spring Boot dev host with a child process, a trigger file and a log marker |
| [0044](0044-development-browser-snapshots-and-explicit-opt-in.md) | Accepted | Send development snapshots over SSE and opt in through the HTML DSL |
| [0045](0045-wogedev-gradle-launcher-and-development-head-hook.md) | Accepted | Run `wogeDev` as a Gradle-launched process and add the client through a guarded head hook |
| [0046](0046-development-client-under-strict-csp.md) | Accepted | Keep the development client working under a strict CSP with nonce reuse and a dev-only header filter |
| [0047](0047-canonical-failure-and-recovery-model.md) | Accepted | Map every failure to exactly one bounded recovery outcome and never replay unsafe requests automatically |
| [0048](0048-document-owned-accessibility-announcements.md) | Accepted | Keep the patch runtime silent and let the page own announcements, focus and busy state per use case |
| [0049](0049-generated-region-descriptors.md) | Accepted | Generate one typed descriptor per `@WogeRegion` HTML function; no string targets and no registry |
| [0050](0050-ksp-inside-wogedev.md) | Accepted | The application applies KSP, the Woge plugin adds the processor, and `wogeDev` regenerates through the normal Gradle build |
| [0051](0051-typed-page-routes.md) | Accepted | `@WogeRoute` on the page input generates one host-neutral route for links and request decoding |
| [0052](0052-typed-action-executors-and-registry.md) | Accepted | Generate explicit typed action executors and allowlisted registries with stable public IDs |
| [0053](0053-bounded-native-form-decoding.md) | Accepted | Decode bounded native UTF-8 forms with generated Kotlin serializers and one cross-host policy |
| [0054](0054-native-form-validation-boundary.md) | Accepted | Retain bounded submitted text and render native field errors through the existing page/action boundary |
| [0055](0055-suspending-webflux-security-context.md) | Accepted | Await reactive security facts in a suspending WebFlux request-context factory |
| [0056](0056-explicit-action-form-enhancement.md) | Accepted | Enhance explicitly opted-in native forms with existing patch streams and never replay uncertain mutations |
| [0057](0057-prepared-typed-action-region-updates.md) | Accepted | Prepare typed action replacements before responding and retain a native canonical redirect |
| [0058](0058-shared-accessible-form-errors.md) | Accepted | Share typed form errors and native 400 HTML while enhanced validation focuses one document-owned summary |
| [0059](0059-identified-collection-patches.md) | Accepted | Append and remove identified collection items with contiguous revisions and explicit removal focus recovery |
| [0060](0060-explicit-browser-owned-state.md) | Accepted | Preserve keyed dirty controls, focus and text selection with explicit reset and native-node ownership |
| [0061](0061-development-full-refresh-state-handoff.md) | Accepted | Preserve explicitly opted-in non-sensitive state around one bounded development reload |
| [0062](0062-latest-intent-and-bounded-region-recovery.md) | Accepted | Register latest browser intent, ignore superseded frames and bound explicit safe region recovery |
| [0063](0063-mutation-request-identities.md) | Accepted | Separate mutation UUIDs, verified authenticity and optional scoped lease-fenced replay reservations |
| [0064](0064-compiler-owned-application-manifest.md) | Accepted | Compose a non-secret application manifest from deterministic compiler metadata and explicit build settings |
| [0065](0065-request-owned-resource-admission.md) | Accepted | Bound deferred admission and pending results, and reject exhausted browser stream budgets without retries |
| [0066](0066-request-owned-native-multipart-uploads.md) | Accepted | Parse bounded native uploads consistently and own temporary file resources for one request |
| [0067](0067-safe-http-cache-defaults-and-conditional-pages.md) | Accepted | Default dynamic HTTP responses to no-store and revalidate authorized pages without rendering |
| [0068](0068-content-addressed-production-asset-trees.md) | Accepted | Package one reproducible asset tree and resolve typed content-hashed URLs without Node |
| [0069](0069-live-invalidations-over-sse.md) | Accepted | Push live region invalidations over SSE and refresh regions with normal authorized GETs |
| [0070](0070-optional-direct-vite-frontend-adapter.md) | Accepted | Offer Vite as an optional, directly invoked frontend adapter; plain HTML/CSS stays Node-free |
| [0071](0071-in-place-stylesheet-updates-in-development.md) | Accepted | Update stylesheets in place during development; other changes still refresh or restart |
| [0072](0072-optional-tailwind-gradle-plugin.md) | Accepted | Ship Tailwind as an optional Gradle plugin that writes the entry file and checks for dynamic class names |
| [0073](0073-native-first-headless-ui-primitives.md) | Accepted | Start headless UI primitives from native HTML elements |
| [0074](0074-ktor-development-restart-parity.md) | Accepted | Run Ktor in `wogeDev` as a managed child that restarts fully after every successful build |
| [0075](0075-experimental-development-mcp-endpoint.md) | Accepted | Offer an experimental, opt-in, loopback-only MCP endpoint in `wogeDev` for coding agents |
| [0076](0076-same-origin-default-for-form-actions.md) | Accepted | Use a built-in same-origin check for form actions |
| [0077](0077-pre-1-0-compatibility-and-version-skew.md) | Accepted | Define pre-1.0 artifact alignment, wire negotiation and adjacent-version skew |

ADRs 0001–0018 are the complete M0 decision set. ADRs beginning with 0019 record implementation-era
decisions and supersessions. The [MVP boundary](../mvp-boundary.md) is the canonical short synthesis;
executable spikes are supporting evidence and do not override an accepted ADR. Issue
[#12](https://github.com/christian-draeger/woge/issues/12) is exempt from a separate ADR because it
indexes and assembles these decisions rather than adding one.

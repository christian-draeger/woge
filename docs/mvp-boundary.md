# Woge MVP boundary

This is the short, reviewable source of truth for what Woge will deliver as its MVP. It assembles the accepted M0 decisions; it does not introduce a new architecture decision.

The MVP spans **M1 through M3**. M1 builds the walking skeleton, M2 completes reactive actions, and M3 hardens and releases the result. M4 is the broader version 1.0 product, not hidden MVP scope.

## Product promise

Woge is an HTML-first Kotlin library for typed, server-driven and progressively enhanced web applications. Web developers keep using HTML elements, URLs, links, forms, HTTP, CSS, browser APIs and normal developer tools. Kotlin adds type-safe references, exhaustive state and compiler feedback; it does not replace the web platform with a Compose-style UI model.

Core navigation and mutations work as useful server-rendered pages without JavaScript. A small browser runtime can apply authorized, revision-checked patches for faster actions and streamed content. The server remains authoritative.

## MVP capabilities

By the end of M3, the MVP includes:

- framework-neutral Kotlin APIs for pages, components, regions, actions and patch outcomes;
- generated typed page URLs, action registrations, component/region references and form commands;
- escaped buffered and streaming HTML with an explicit `kotlinx.html` interop boundary;
- structured deferred regions and a length-framed, transport-neutral patch protocol;
- a small owned cross-browser runtime for replace, append, remove and announce operations;
- ordinary links and Post/Redirect/Get forms plus enhanced form requests, validation and multi-region updates;
- page epochs, target revisions, stable keyed identity and focus/dirty-control preservation;
- authorized Server-Sent Events (SSE), cancellation, backpressure and reconnect diagnostics;
- first-class Spring MVC, Spring WebFlux and Ktor adapters behind one host SPI and adapter TCK;
- Spring Boot starter/auto-configuration as the primary setup and documentation path;
- ordinary current CSS, IDE-recognized CSS literals and optional deterministic build-time scoping;
- optional Tailwind build integration with explicit Kotlin/generated/registry source discovery;
- an accessible binary headless UI primitive foundation that applications style with ordinary CSS or Tailwind;
- strict output, CSRF, authorization, CSP, accessibility, browser and performance hardening;
- published JVM artifacts, coherent Gradle tasks and executable web-first documentation;
- compile-verified examples and scored AI-DX checks using the same supported public APIs as humans.

The optional native Declarative Partial Updates encoder may appear in M3 only as an off-by-default Chrome 150+ initial-document optimization. The cross-browser runtime remains the supported path, and the MVP may ship with no native encoder enabled.

## Host support and parity

| Host | MVP status | Promise |
| --- | --- | --- |
| Spring Boot + Spring MVC | First-class; primary getting-started path | Full page, action, patch and live-update semantics through the shared TCK |
| Spring Boot + Spring WebFlux | First-class; primary reactive Spring path | The same Woge semantics and TCK, with non-blocking execution and explicit isolation for blocking work |
| Ktor | First-class Kotlin alternative | The same portable application/component API and TCK; secondary, host-specific setup documentation |

Parity means equivalent Woge-visible status, headers, HTML/patch meaning, validation, authorization, ordering and cancellation outcomes. It does not mean identical framework APIs, threads, buffering, memory use or throughput. Spring and Ktor types never enter the portable core. A Spring Boot application selects exactly one Spring web adapter and fails clearly when both are ambiguous.

## Explicit MVP non-goals

- No client-rendered SPA core, virtual DOM, application-wide hydration or mandatory JavaScript.
- No Compose-style HTML abstraction, comprehensive Kotlin CSS-property DSL or typed Tailwind utility clone.
- No Kotlin/JS or Kotlin/Wasm island runtime ships in the MVP. The optional, bounded island target is decided in [#53](https://github.com/christian-draeger/woge/issues/53) for M4.
- No enhanced page-navigation runtime in the MVP. Real links remain the baseline; enhancement is [#51](https://github.com/christian-draeger/woge/issues/51) in M4.
- No WebSocket transport in the MVP. SSE is the supported live transport; optional WebSocket work is [#57](https://github.com/christian-draeger/woge/issues/57) in M4.
- No source-owned registry or broad MUI-style packaged visual library in the MVP. M3 establishes accessible headless primitives; the registry [#81](https://github.com/christian-draeger/woge/issues/81), styled catalog and complex theme land in M4.
- No `kotlinx.rpc` or RPC-shaped public application model. Typed HTTP pages, forms, actions and streams are the chosen boundary; a future RPC adapter would require separate evidence and an ADR.
- No built-in database, ORM, identity provider or authorization policy. Applications retain those decisions; Woge adapters carry the required typed context.
- No stable native-DPU dependency or Chromium-only product behavior.

## MVP definition of done

The MVP is releasable only when all three gates pass:

1. **M1 — walking skeleton:** one shared application streams its shell and deferred region through Spring MVC, Spring WebFlux and Ktor; adapter TCK, browser/JVM CI, executable newcomer guide and first scored AI-DX consumer run pass.
2. **M2 — reactive actions:** the reference CRUD journey covers typed routes/forms/actions, validation, multi-region patches, revisions and authorized SSE with useful HTML-only fallbacks; optional Tailwind produces the same semantics as plain CSS.
3. **M3 — hardening and release:** stable-browser/no-JavaScript, accessibility, XSS/CSRF/CSP, malformed-frame, cancellation/proxy, performance/bundle, Spring Boot production and publication checks pass with recorded environments and limits.

Documentation is part of the product gate. Canonical examples compile in CI, start from visible web behavior, introduce Kotlin only when needed, and show real diagnostics and escape hatches. AI evaluation uses those same examples and APIs; AI-only aliases or undocumented conventions do not count as success.

## Owned risks

| Risk | Backlog owner |
| --- | --- |
| Public APIs or module edges harden before a real consumer exists | Scaffold and graph [#13](https://github.com/christian-draeger/woge/issues/13), [#14](https://github.com/christian-draeger/woge/issues/14); consumer AI-DX run [#93](https://github.com/christian-draeger/woge/issues/93) |
| MVC, WebFlux and Ktor drift or hide lifecycle differences | Shared TCK [#65](https://github.com/christian-draeger/woge/issues/65), cancellation/proxy evidence [#45](https://github.com/christian-draeger/woge/issues/45), Spring production hardening [#58](https://github.com/christian-draeger/woge/issues/58) |
| Stream/parser/HTML boundaries permit injection or ambiguous failure | Fuzzing [#41](https://github.com/christian-draeger/woge/issues/41), XSS hardening [#42](https://github.com/christian-draeger/woge/issues/42), CSP/Trusted Types [#43](https://github.com/christian-draeger/woge/issues/43) |
| Patches break focus, dirty fields or cross-browser behavior | Preservation [#36](https://github.com/christian-draeger/woge/issues/36), browser conformance [#39](https://github.com/christian-draeger/woge/issues/39), accessibility audit [#44](https://github.com/christian-draeger/woge/issues/44) |
| Styling or component convenience compromises web-native ownership | Asset contract [#78](https://github.com/christian-draeger/woge/issues/78), Tailwind adapter [#79](https://github.com/christian-draeger/woge/issues/79), headless primitives [#80](https://github.com/christian-draeger/woge/issues/80) |
| Convenience adds unmeasured runtime/build cost | Performance and bundle budgets [#46](https://github.com/christian-draeger/woge/issues/46), coherent build tasks [#47](https://github.com/christian-draeger/woge/issues/47) |
| Documentation, diagnostics or generated APIs work only for maintainers | Executable guide [#73](https://github.com/christian-draeger/woge/issues/73), examples/diagnostics [#74](https://github.com/christian-draeger/woge/issues/74), design-partner release [#50](https://github.com/christian-draeger/woge/issues/50) |

## M0 exit review

- The project-operations reference application and measurable complex-screen contract are fixed.
- All other [M0 milestone issues](https://github.com/christian-draeger/woge/milestone/1?closed=1) are closed with evidence.
- ADRs 0001–0018 are accepted and listed in the [decision index](adr/README.md).
- Canonical guidance is separated from executable spike evidence in the [documentation index](README.md).
- Host, browser, security, protocol, CSS, Tailwind, component, documentation and AI-DX boundaries have implementation backlog owners.
- Closing [#12](https://github.com/christian-draeger/woge/issues/12) completes M0; changes to these boundaries require the normal ADR lifecycle.

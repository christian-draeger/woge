# ADR 0006: Enforce a small inward-pointing initial module graph

- Status: Accepted
- Date: 2026-09-03
- Decision owners: Woge maintainers
- Related issues: [#9](https://github.com/christian-draeger/woge/issues/9), [#18](https://github.com/christian-draeger/woge/issues/18), [#65](https://github.com/christian-draeger/woge/issues/65), [#66](https://github.com/christian-draeger/woge/issues/66), [#67](https://github.com/christian-draeger/woge/issues/67), [#68](https://github.com/christian-draeger/woge/issues/68), [#69](https://github.com/christian-draeger/woge/issues/69)

## Context

Woge needs enough separation to keep Spring MVC, Spring WebFlux and Ktor replaceable without turning the first build into dozens of tiny artifacts. The server [use-case ports](0005-server-host-use-case-ports.md) define ownership at runtime; the build must make the same boundary difficult to violate accidentally.

The module graph also needs honest distinctions between stable user-facing API, implementation artifacts and reusable test support. Browser patch engines, local islands, CSS compilation and code generation still have unresolved M0 decisions and must not force dependencies into the server core prematurely.

## Decision

The initial build has ten modules. Every production dependency points inward toward Woge-owned web concepts and no host adapter depends on another host adapter.

| Module | Responsibility | Exposure | Allowed direct dependencies |
| --- | --- | --- | --- |
| `woge-core` | HTML/component application model and portable value types | Public | None |
| `woge-protocol` | Host-neutral document, patch and live frame values | Public | Core |
| `woge-host-spi` | Page, action and live-update use-case ports | Public | Core, protocol |
| `woge-server-runtime` | Shared dispatch and execution implementation | Internal implementation artifact | Core, protocol, host SPI |
| `woge-adapter-tck` | Reusable adapter contract fixtures | Test-support artifact | Core, protocol, host SPI, runtime |
| `woge-spring-mvc` | Servlet/Spring MVC transport mapping | Public integration | Core, protocol, host SPI, runtime |
| `woge-spring-webflux` | Reactive Spring WebFlux transport mapping | Public integration | Core, protocol, host SPI, runtime |
| `woge-ktor` | Ktor transport mapping | Public integration | Core, protocol, host SPI, runtime |
| `woge-spring-boot-autoconfigure` | Conditional Spring Boot wiring and diagnostics | Public integration | Host SPI and optional MVC/WebFlux adapters |
| `woge-spring-boot-starter` | Spring Boot dependency entry point | Public integration | Auto-configuration only |

The source paths and exact required/optional dependency edges live in the machine-readable [`module-boundaries.tsv`](../../config/architecture/module-boundaries.tsv). The manifest is the source of truth until the Gradle scaffold exists; Gradle project declarations must then agree with it.

`woge-core`, `woge-protocol`, `woge-host-spi` and `woge-server-runtime` are portable. Their source may not import Spring, Reactor, Servlet or Ktor packages. The first three are intentional public API surfaces. `woge-server-runtime` is published only as an implementation dependency required by adapters; its packages and declarations are documented as internal and are excluded from compatibility promises.

The TCK depends inward and exports only Woge-owned fixtures and a small adapter-harness contract. Adapter production code never depends on the TCK. Each adapter consumes it from its test source set, so a framework-specific fixture cannot become part of the portable application corpus.

Spring Boot auto-configuration may compile against both adapter APIs as optional dependencies, but it does not make both web stacks runtime dependencies. The manifest records those edges separately from required dependencies. Auto-configuration activates exactly one adapter based on the application classpath, following the [Spring support model](../architecture/spring-support-model.md). The starter owns the convenient entry point and auto-configuration metadata; transport behavior stays in the separate adapter artifacts.

The initial graph deliberately excludes browser patch runtimes, a native-DPU encoder, local-island runtimes, CSS scoping/build tooling, component distribution and generated-descriptor processors. Their M0 spikes decide whether each deserves a module. Examples and benchmark applications are build consumers, not production modules.

CI runs [`validate-module-boundaries.sh`](../../scripts/validate-module-boundaries.sh). It validates dependency existence, role direction, duplicate paths and cycles. As source directories appear, the same check rejects host-framework imports from portable modules. M1 adds Gradle dependency verification and public binary-API validation when there are compiled artifacts to inspect.

## Alternatives considered

- **One artifact containing core and all adapters:** rejected because it leaks host dependencies, pulls incompatible web stacks together and prevents meaningful architecture checks.
- **A separate module for every conceptual type immediately:** rejected because module count would encode untested assumptions and make the walking skeleton expensive to change.
- **Spring as the internal runtime abstraction:** rejected because Ktor would become a wrapper around Spring concepts and portable APIs could accidentally expose Reactor or Servlet types.
- **Ktor as the internal runtime abstraction:** rejected for the symmetrical reason; Ktor remains a first-class adapter, not the definition of Woge's host boundary.
- **Put TCK fixtures in each adapter:** rejected because semantic parity would drift and framework-specific tests could silently replace the shared contract.
- **A starter that depends on both Spring web stacks:** rejected because classpath selection becomes ambiguous and every application pays for an unused stack.
- **Create browser and styling modules now:** rejected until framing, fallback runtime, DPU, CSS and island spikes establish their actual boundaries.

## Consequences

### Positive

- Framework leakage is prevented by both dependency direction and source checks.
- Spring MVC, WebFlux and Ktor can evolve independently against one host contract.
- Spring Boot remains the primary ergonomic entry point without owning transport semantics.
- Internal implementation and test-support artifacts are visibly different from promised public APIs.
- Unresolved browser and styling decisions cannot distort the first server build.

### Negative

- Ten modules are more build machinery than a single proof-of-concept artifact.
- Auto-configuration needs optional classpath handling and explicit ambiguity diagnostics.
- The server runtime crosses a module boundary despite not being public API.
- Source-import checks are an early guardrail, not a substitute for compiled dependency and binary-API analysis.

## Follow-up

- Materialize the manifest in the Gradle scaffold and add dependency-verification tests in [#13](https://github.com/christian-draeger/woge/issues/13).
- Add public binary-API validation while implementing the core and host SPI in [#17](https://github.com/christian-draeger/woge/issues/17) and [#18](https://github.com/christian-draeger/woge/issues/18).
- Consume `woge-adapter-tck` only from adapter test source sets in [#65](https://github.com/christian-draeger/woge/issues/65).
- Verify that the Spring Boot starter does not pull both web stacks in [#69](https://github.com/christian-draeger/woge/issues/69).
- Add browser, CSS, island or code-generation modules only after their M0 ADRs establish a proven boundary.

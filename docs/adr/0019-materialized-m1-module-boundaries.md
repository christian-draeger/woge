# ADR 0019: Materialize the M1 module and consumer boundaries

- Status: Accepted
- Date: 2026-09-04
- Decision owners: Woge maintainers
- Related issues: [#14](https://github.com/christian-draeger/woge/issues/14), [#20](https://github.com/christian-draeger/woge/issues/20), [#21](https://github.com/christian-draeger/woge/issues/21), [#24](https://github.com/christian-draeger/woge/issues/24), [#80](https://github.com/christian-draeger/woge/issues/80)

## Context

[ADR 0006](0006-initial-module-boundaries.md) selected ten inward-pointing JVM modules before the
M0 frontend and component investigations were complete. [ADR 0018](0018-hybrid-headless-and-source-owned-components.md)
subsequently added a host-neutral `woge-ui-headless` binary boundary. The accepted fallback client
and reference application also need visible ownership without pretending they are JVM libraries or
allowing them to shape portable APIs.

The Gradle build now exists, so a prose graph and a disconnected manifest are no longer sufficient.
Project paths, declared dependencies and architectural checks must describe the same graph.

## Decision

The M1 production JVM graph consists of these eleven artifacts:

| Module | Ownership | Exposure | Direct Woge dependencies |
| --- | --- | --- | --- |
| `woge-core` | HTML writer/component model and portable values | Public | None |
| `woge-ui-headless` | Accessible, host- and styling-neutral UI behavior | Public | Core |
| `woge-protocol` | Document, patch and live-frame values | Public | Core |
| `woge-host-spi` | Page, action and live-update host ports | Public | Core, protocol |
| `woge-server-runtime` | Shared dispatch and execution implementation | Internal | Core, protocol, host SPI |
| `woge-adapter-tck` | Reusable server-adapter contract fixtures | Test support | Core, protocol, host SPI, runtime |
| `woge-spring-mvc` | Spring MVC/Servlet transport translation | Public integration | Core, protocol, host SPI, runtime |
| `woge-spring-webflux` | Spring WebFlux transport translation | Public integration | Core, protocol, host SPI, runtime |
| `woge-ktor` | Ktor transport translation | Public integration | Core, protocol, host SPI, runtime |
| `woge-spring-boot-autoconfigure` | Conditional adapter wiring and diagnostics | Public integration | Host SPI; optional MVC/WebFlux adapters |
| `woge-spring-boot-starter` | Spring Boot dependency entry point | Public integration | Auto-configuration |

The machine-readable [`module-boundaries.tsv`](../../config/architecture/module-boundaries.tsv)
drives Gradle project inclusion and is the source of truth for project names, paths, roles, exposure,
required dependencies and optional dependencies. Every module applies the same JVM library convention.
Required project dependencies use `api` or `implementation`; optional adapter discovery uses
`compileOnly`, so auto-configuration does not pull both Spring web stacks into an application.

HTML authoring remains part of `woge-core`; it is a web-facing API, not a separate rendering engine.
The optional `kotlinx.html` bridge remains deferred until the core sink has a real consumer. Headless
UI depends only on core and cannot depend on a host adapter, CSS toolchain or browser runtime.

The fallback browser adapter is owned by `client/woge-fallback-client` as an independently tested web
artifact. The reference application is owned by `examples/reference-application` as a consumer of
public artifacts with separate Spring MVC, WebFlux and Ktor launchers. Neither is a production JVM
library in the module manifest.

The boundary gate verifies manifest validity, allowed role direction, exact production project
dependencies, optional dependency configuration, duplicate paths, cycles, MVC/WebFlux independence
and the absence of Spring, Reactor, Servlet or Ktor references in portable production sources.
Non-public artifacts are not connected to Maven publishing by the M1 build.

WebSocket transport, Kotlin/Wasm and Kotlin/JS islands remain documented extension points only. They
receive modules only after a concrete use case and ADR establish their API, lifecycle and fallback
contract.

This ADR supersedes [ADR 0006](0006-initial-module-boundaries.md) by carrying its constraints forward
and adding the accepted headless-UI and non-JVM consumer boundaries.

## Alternatives considered

- **Keep the original ten modules:** rejected because it would ignore the accepted headless-component
  boundary and invite primitives into core or application recipes.
- **Make the browser client a Kotlin/JS module now:** rejected because the accepted runtime is a small
  standards-native protocol adapter and no shared-Kotlin requirement has been demonstrated.
- **Add the reference application to the production manifest:** rejected because examples consume and
  verify public artifacts; they are not dependencies of those artifacts.
- **Create modules now for WebSocket, Wasm, CSS compilation and `kotlinx.html`:** rejected because each
  remains an optional extension with separate evidence and delivery work.
- **Let every Gradle file declare an unchecked graph:** rejected because drift between the ADR,
  manifest and executable build would be found only through accidental compilation failures.

## Consequences

### Positive

- Spring MVC, WebFlux and Ktor can evolve independently around one portable host contract.
- Headless components have a stable home without introducing styling or host dependencies.
- The browser runtime and reference application are visible without contaminating the JVM graph.
- Gradle and CI fail on undeclared dependencies, cycles and portable framework leakage.
- Internal and test-support artifacts remain visibly distinct from promised public APIs.

### Negative

- Eleven mostly empty projects add build configuration before their implementation issues land.
- A manifest and conventional dependency syntax must be maintained together.
- Source scanning is intentionally stricter than public-signature scanning and may reject even an
  internal host-framework reference in a portable module.
- Browser-client and example build graphs need their own CI checks when they become executable.

## Follow-up

- Add JVM and browser matrices in [#15](https://github.com/christian-draeger/woge/issues/15).
- Implement HTML/core behavior in [#16](https://github.com/christian-draeger/woge/issues/16) and
  [#17](https://github.com/christian-draeger/woge/issues/17).
- Add binary API validation when public declarations arrive in [#17](https://github.com/christian-draeger/woge/issues/17)
  and [#18](https://github.com/christian-draeger/woge/issues/18).
- Build and consume the adapter TCK in [#65](https://github.com/christian-draeger/woge/issues/65).
- Implement the headless primitive contract in [#80](https://github.com/christian-draeger/woge/issues/80).

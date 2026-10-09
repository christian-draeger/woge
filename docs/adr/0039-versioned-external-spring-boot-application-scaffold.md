# ADR 0039: Maintain one versioned external Spring Boot application scaffold

- Status: Accepted
- Date: 2026-09-14
- Decision owners: @christian-draeger
- Related issues: [#143](https://github.com/christian-draeger/woge/issues/143), [#48](https://github.com/christian-draeger/woge/issues/48), [#149](https://github.com/christian-draeger/woge/issues/149)

## Context

The root-build reference application proves Woge's internal multi-host vertical slice, but it does
not prove that a new application can consume only normal artifact coordinates outside the Woge
checkout. A useful starter also needs a clear Spring choice without forcing a new user to understand
the complete adapter architecture, frontend toolchain or future development orchestrator first.

The scaffold is simultaneously human and machine-facing documentation. If versions, generated-source
ownership or canonical examples drift, developers and coding agents receive plausible but invalid
instructions. If it starts with a JavaScript build, Tailwind or Ktor, optional choices become accidental
requirements. If it renders strings instead of typed markup, it teaches the wrong application shape.

## Decision

Woge maintains one small, versioned source template in `scaffolds/spring-boot`. A materializer copies
that source plus the repository's checked-in Gradle wrapper into an empty external directory. The
resulting build has no Gradle project dependency or repository-relative source dependency; it consumes
Woge by Maven coordinates.

Spring Boot WebFlux is the default host. Spring MVC is selected explicitly with
`-PwogeSpringAdapter=mvc`. Gradle compiles only the selected host source set and installs only that
host's Spring and Woge adapter. The portable page is unchanged and imports no Spring, Reactor or
Servlet type. This is an initial host choice, not an application-architecture prescription.

The first page uses Woge's type-safe HTML DSL, semantic elements, ordinary URLs and a normal external
stylesheet. Its CSS uses current browser capabilities directly. No script is required, and the default
application installs neither Tailwind, Vite nor Ktor. Node and Playwright exist only as an optional
browser-test harness.

`scaffold.properties` versions the scaffold contract and records its default host and generated-source
root. Woge generators may own only `build/generated/ksp/main/kotlin` (changed by
[ADR 0050](0050-ksp-inside-wogedev.md)); developers own `src`, and generated output is not committed.
Woge, Kotlin, KSP, Spring Boot, build-JDK and JVM-target versions are explicit and checked against
repository metadata.

Repository verification publishes the exact Woge dependency closure needed by the scaffold into a
temporary build-local Maven repository, materializes fresh external WebFlux and MVC applications and
runs their real-server tests. This test-only publication does not define public release policy,
credentials or remote repositories; issue #48 still owns those concerns. A separate Chromium gate
disables JavaScript and verifies semantic HTML, loaded CSS and useful content on an available local
port. CI retains its browser evidence.

The layout and toolchain defaults selectively follow `christian-draeger/kotlin-library-template`
commit `33103bcaf6015f038266e41c2309e6f522ec00f8`; library publication and release automation are not
copied into the application.

## Alternatives considered

- **Keep only the root-build reference application:** it gives broader framework coverage, but can
  accidentally rely on composite-build wiring and does not represent a user's first repository.
- **Generate files from an opaque plugin immediately:** convenient later, but makes the initial source
  contract harder to review and couples scaffold design to unfinished development tooling.
- **Ship separate WebFlux and MVC templates:** each is simpler internally, but shared application files
  can drift and make one framework look structurally privileged.
- **Install Tailwind or Vite by default:** useful for some complex applications, but unnecessary for a
  first standards-native page and would turn Node into a baseline requirement.
- **Use string concatenation for the first page:** superficially small, but forfeits Kotlin completion,
  contextual escaping and the canonical Woge markup model.

## Consequences

### Positive

- Every pull request proves a realistic Maven-coordinate consumer outside the repository build.
- Web developers start from recognizable HTML, CSS, routes and HTTP while gaining Kotlin type safety.
- WebFlux and MVC remain explicit, symmetric choices around one portable page.
- The versioned metadata and compiled canonical source give coding agents a stable, searchable entry
  point.
- Optional frontend tooling can be added later without defining Woge's core application boundary.

### Negative

- The root build has a build-local publication path and two additional external integration builds.
- Two tiny host route files are maintained until generation or higher-level adapter APIs earn a simpler
  shape.
- The copied wrapper follows the Woge repository until a public scaffold distribution mechanism exists.
- The Browser gate uses Node as test infrastructure even though generated applications do not require
  it at runtime or for normal JVM tests.

## Follow-up

- Use this scaffold as the canonical consumer for `wogeDev` in
  [#147](https://github.com/christian-draeger/woge/issues/147).
- Generate bounded, repository-local coding-agent guidance in
  [#149](https://github.com/christian-draeger/woge/issues/149).
- Replace build-local coordinates with the reviewed snapshot/release path from
  [#48](https://github.com/christian-draeger/woge/issues/48).

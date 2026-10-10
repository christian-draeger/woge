# Maintain the Spring Boot application scaffold

The canonical starter source lives in [`scaffolds/spring-boot`](../../scaffolds/spring-boot/README.md).
It is an application template, not another Woge module or a replacement for the multi-host reference
application. The scaffold proves what a new repository sees: Maven coordinates, a normal Spring Boot
build, one type-safe HTML page and ordinary CSS.

## Materialize a local application

From the Woge checkout:

```shell
./scripts/materialize-spring-boot-scaffold.sh ../my-woge-application
./scripts/materialize-spring-boot-scaffold.sh ../my-mvc-woge-application mvc
```

The target must be absent or empty. The script copies the versioned application files and the checked-in
Gradle wrapper; it does not create links back to Woge source. Before public Woge artifacts exist, use
the repository verification tasks below rather than hand-editing dependency coordinates.

WebFlux is the default. The optional `mvc` argument persists MVC as `wogeSpringAdapter`. Both choices
compile the same `src/main` application and exactly one small host source set. The materializer also
generates a version- and host-matched root `AGENTS.md`; Tailwind, Vite and Ktor are not installed by
default.

## Verify the consumer boundary

```shell
./gradlew testSpringBootScaffold
./gradlew scaffoldBrowserSmoke
```

`testSpringBootScaffold` first validates pinned versions, metadata and required files. It publishes the
needed Woge modules to `build/scaffold-maven-repository`, creates clean external WebFlux and MVC copies,
and runs each copy's real-server test. It also runs the fresh WebFlux copy with a temporary MVC
override, without modifying its generated guidance. The publication is disposable build evidence, not a local or
remote release.

`scaffoldBrowserSmoke` materializes another clean WebFlux copy and runs Chromium with JavaScript
disabled. It checks semantic content, the external stylesheet and the absence of script elements. The
test chooses an available loopback port so it can coexist with a developer's running application.
Reports and failure evidence are copied to `build/reports/scaffold-browser` before the temporary app is
removed.

The JVM consumer test is part of root `check`; the focused browser test is part of the browser CI job.
No documentation-only copy of the Kotlin page exists: the compiled scaffold source is canonical.

## Ownership and safe evolution

- Edit application-owned source under `scaffolds/spring-boot/src`.
- Reserve `build/generated/ksp/main/kotlin` for the Woge KSP processor and never commit it.
- Update `scaffoldVersion` when a consumer-visible template contract changes.
- Keep Woge, Kotlin and Spring Boot versions aligned through
  `scripts/validate-spring-boot-scaffold.sh`.
- Change the canonical [application guidance template](../ai-dx/application-agents.template.md) or
  framework index, then regenerate `AGENTS.md`; never patch the generated copy alone.
- Keep the default page useful without JavaScript and the default dependency set free of optional
  frontend pipelines.
- Prefer implementation plus external tests for clear behavior. Use a spike only when an uncertain
  process, browser or tool boundary cannot be validated cheaply in this maintained consumer.

The durable rationale is recorded in
[ADR 0039](../adr/0039-versioned-external-spring-boot-application-scaffold.md).

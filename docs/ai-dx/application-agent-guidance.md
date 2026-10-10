# Version-matched application guidance

Every materialized Woge Spring Boot application receives a concise root `AGENTS.md`. It tells coding
agents which Woge/Kotlin/Spring versions and host the application actually selected, then points them
to the same public guides and compile-verified examples a human developer uses. It does not define a
second application API or a private prompt surface.

## Sources and generated output

Four inputs have separate ownership:

- [`woge-framework-index.json`](woge-framework-index.json) owns framework/tool versions, canonical
  guides and public example locations.
- [`application-agents.template.md`](application-agents.template.md) owns compact behavioral guidance
  and valid/invalid application shapes.
- The application scaffold's `gradle.properties` and `scaffold.properties` own selected host,
  scaffold version and generated-source location.
- [`generate-spring-boot-agent-guidance.sh`](../../scripts/generate-spring-boot-agent-guidance.sh)
  combines those inputs into the scaffold's generated
  [`AGENTS.md`](../../scaffolds/spring-boot/AGENTS.md).

Refresh the committed WebFlux-default copy from the repository root:

```shell
./scripts/generate-spring-boot-agent-guidance.sh scaffolds/spring-boot/AGENTS.md
```

Do not edit the generated copy directly. Change canonical metadata or the template and regenerate.
The scaffold validator generates a clean copy and compares it byte-for-byte, so a Woge or Kotlin
version bump cannot leave plausible but stale guidance behind.
The materialized application's normal Gradle test also compares its pinned Woge, Kotlin and Spring
versions plus selected host with the generated file and fails with a regeneration instruction.

## Per-application host selection

The materializer accepts `webflux` (default) or `mvc`:

```shell
./scripts/materialize-spring-boot-scaffold.sh ../my-woge-app mvc
```

It persists the choice as `wogeSpringAdapter` before generating the application's `AGENTS.md`. Both
the build and guidance therefore name the same selected host. Optional Tailwind/Vite setup remains
absent; Playwright is identified as test-only tooling.

A temporary `-PwogeSpringAdapter=mvc` build override does not change the saved default or rewrite
guidance. Its normal `check` verifies guidance against the persisted host. For a permanent switch,
edit `wogeSpringAdapter` in the application's `gradle.properties`, then run this command from the
application root using the matching Woge checkout:

```shell
/path/to/woge/scripts/generate-spring-boot-agent-guidance.sh AGENTS.md .
```

## Deterministic verification

`./gradlew :woge-m1-api-corpus:check` verifies that the framework index points to the real generator,
template, generated file and human guides. It also requires the output to contain the current
versions, selected host, generated-source boundary and canonical page/form/region/safety guidance.
`./gradlew testSpringBootScaffold` then materializes and tests both host choices as independent Maven
consumers.

Model runs are evaluation evidence, not nondeterministic pull-request checks. The multi-model-family
consumer run remains owned by [#93](https://github.com/christian-draeger/woge/issues/93), where exact
model/version, prompts, corrections and hard-gate results can be recorded using the public
[evaluation protocol](evaluation.md).

The source-of-truth and no-agent-only-API decision is recorded in
[ADR 0040](../adr/0040-generate-application-agent-guidance-from-public-metadata.md).

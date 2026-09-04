# Contributing to Woge

Woge is early in development. Prefer small changes that prove one behavior and leave a clear path for the next vertical slice.

## Architecture decisions

Create or update an ADR when a change affects one of these contracts:

- public Kotlin API;
- browser or server protocol;
- module or dependency boundaries;
- security defaults or trust boundaries;
- supported server, browser or build platforms;
- compatibility promises.

Small local implementation choices do not need an ADR. If a pull request intentionally does not require one, explain why in the pull-request template.

Read the [ADR lifecycle](docs/adr/README.md) before proposing a decision. Do not edit the decision of an accepted ADR as though history changed; supersede it with a new ADR.

## Documentation

Public behavior needs documentation in the same change. Write for a web developer who may be new to Kotlin and follow the [documentation style guide](docs/documentation/style-guide.md).

Examples must be complete enough to compile once the corresponding modules exist. Canonical examples will be tested and reused by tutorials instead of copied into several files.

## Spikes and experiments

Read the [spike lifecycle and inventory](spikes/README.md) before changing an experiment. Frozen spikes are evidence, not reusable modules: production code must not depend on them, and accepted conclusions change through a new ADR. Move durable tests to production before retiring a spike.

## Local checks

The repository scaffold will eventually expose one Gradle verification task. Until then, validate the current documentation foundation with:

```shell
./scripts/validate-adrs.sh
```

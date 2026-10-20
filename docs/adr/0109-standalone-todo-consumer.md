# ADR 0109: Maintain a standalone Todo consumer over published artifacts

- Status: Accepted
- Date: 2026-10-20
- Decision owners: @christian-draeger
- Related issues: [#265](https://github.com/christian-draeger/woge/issues/265), [#270](https://github.com/christian-draeger/woge/issues/270)

## Context

The operations reference verifies host parity and complex UI behavior. A newcomer also needs a small
application they can copy out of the framework repository and start with normal published dependencies.
The notes and board already establish the required action/validation/region boundaries.

## Decision

Maintain [a standalone Spring Boot Todo application](../../examples/todo-spring-boot/README.md) with
its own Gradle wrapper and a pinned public Woge version. Add, complete, delete and filter share
one synchronized, process-local repository. Native forms and enhancement execute the same actions.
One live board region contains both list and count; the add form remains outside it to preserve drafts.

The root verification materializes the application in a temporary directory and resolves newly built
Maven artifacts through the explicit repository/version override. A separate CI job verifies the
checked-in public version through GitHub Packages using a package-read-only token. Neither path uses
a composite build. Browser checks run the production JAR, including the no-JavaScript path.

Keep the operations application as the multi-host/complex-screen reference. The Todo consumer is a
small entry point and a shared fixture for authoring, tests, images and agent-workflow evidence.

## Alternatives considered

- **Only expand the operations board:** useful internal evidence but too much setup/domain code for
  the first consumer task.
- **Copy framework source or use an included build:** hides publication, module metadata and plugin
  resolution problems that ordinary consumers encounter.
- **Add a database:** unnecessary to demonstrate these contracts. Applications own durable storage.

## Consequences

The example exposes publication and compatibility failures directly. It also needs its public version
updated deliberately after API changes. The shared memory store is a public demo, loses data at restart
and cannot provide a replicated application store. Basic use needs no Node; Node is browser-test-only.

## Follow-up

Use #266/#267 to measure convenience and consumer-testing needs. #270 owns OCI delivery. Human and
design-partner trials remain in #49/#181/#253/#50; automated tests do not claim their completion.

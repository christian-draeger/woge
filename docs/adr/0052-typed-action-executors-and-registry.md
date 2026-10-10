# ADR 0052: Generate explicit typed action executors

- Status: Accepted
- Date: 2026-10-10
- Decision owners: Woge maintainers
- Related issues: [#28](https://github.com/christian-draeger/woge/issues/28), [#29](https://github.com/christian-draeger/woge/issues/29)
- Refines: [ADR 0011](0011-typed-web-references.md)

## Context

Forms need stable action URLs and typed commands. A client-supplied class or function name must
never select arbitrary server code. The same action must run with request facts copied from MVC,
WebFlux or Ktor, without importing any of those hosts.

## Decision

Annotate a top-level suspend function with `@WogeAction("create-task")`. It takes a non-null data
class command and `RequestContext`, and returns `PageResult`. The explicit ID is independent of
Kotlin package or function names; renaming a function does not change its public URL.

KSP generates `CreateTaskAction : ActionDescriptor<CreateTask>`. It implements
`ActionExecutor<CreateTask>` by calling the function directly with the typed command and context.
There is no reflection, runtime class loading, command cast or generic invocation by name.

The descriptor exposes an ordinary form URL, `/woge-actions/create-task`. Host route files still
register the POST endpoint explicitly. Markup uses the normal Woge `form` and URL attribute helpers.

Each package gets an explicit `wogeActions` registry. Duplicate IDs across the module and duplicate
descriptor names within a package fail the build. Applications can combine package registries in
an `ActionRegistry`, which also rejects duplicate IDs across modules. Registry lookup returns only
a registered descriptor; it cannot execute an untyped command.

Commands use scalar form values, enums, value classes and lists of those values. Unsupported command,
context or return shapes fail with located `WOGE-ACTION` diagnostics. Return values are deliberately
limited to existing page outcomes: HTML, a redirect or a controlled failure.

Generated action files and registries are aggregating KSP outputs. They track the action and command
sources, so additions, removals and ID changes participate in the module-wide collision check.
Build-time signature validation is separate from parsing submitted fields.

Authentication and CSRF remain host ingress responsibilities. Domain authorization belongs inside
the action. A generated descriptor is not permission to call it and does not bypass security.

## Alternatives considered

- **Reflective dispatch using a class/function name:** rejected; it makes the callable surface
  implicit and exposes runtime invocation machinery.
- **Generate Spring controllers:** rejected; it would make the portable API host-specific.
- **Infer IDs from qualified names:** rejected; ordinary refactoring would break public form URLs.
- **Arbitrary action return types:** deferred; existing page outcomes already express normal HTTP
  form behavior without another result protocol.

## Consequences

### Positive

- Commands cannot be passed to the wrong descriptor.
- IDs remain stable across Kotlin refactors.
- Native forms and normal HTTP remain visible, with no JavaScript requirement.
- Generated code is explicit and compiler-checked.

### Negative

- Actions in separate modules need a combined registry to detect cross-module duplicate IDs.
- Aggregating outputs reprocess the small action set when an action source changes.
- Applications must preserve public IDs when changing Kotlin names.

## Follow-up

- Finish the shared POST dispatch and security-context adapter contract in
  [#28](https://github.com/christian-draeger/woge/issues/28).
- Add bounded, authoritative URL-encoded command decoding in
  [#29](https://github.com/christian-draeger/woge/issues/29).

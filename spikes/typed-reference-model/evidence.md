# Typed reference evidence

Recorded on 2026-09-03 with Kotlin 2.4.0 and the repository Gradle 8.14.4 wrapper.

## Positive cases

Three tests compile and pass:

1. a `PageRef` produces an ordinary percent-encoded URL while an `ActionRef` creates a typed invocation;
2. `TaskRow` creates two distinct rendered instances from `TaskId(1)` and `TaskId(2)`;
3. the generated `TaskRow.status` slot binds to each matching instance and `UpdateBuilder.replace` accepts only its `TaskStatusView` input.

This is the minimal valid target shape:

```kotlin
val row = TaskRow.instance(
    key = TaskId(1),
    props = TaskRowProps("Write docs", completed = false),
)
val status = TaskRow.status.of(row, TaskStatusView(completed = true))

UpdateBuilder().replace(status, TaskStatusView(completed = true))
```

Multiple component instances do not need generated names such as `TaskRow1` and `TaskRow2`; the descriptor type stays `TaskRow.Type`, while the typed key distinguishes each `ComponentInstance`.

## Negative compiler fixtures

All diagnostics are source-located and report actual versus expected concepts.

Using a page descriptor as a patch target:

```text
PageAsRegion.kt:8:29 Argument type mismatch: actual type is 'ProjectPage',
but 'RegionInstance<..., TaskStatusView>' was expected.
```

Binding a `TaskRow` region to a rendered `ProjectCard`:

```text
WrongComponentOwner.kt:11:23 Argument type mismatch:
actual type is 'ComponentInstance<ProjectCard.Type, ProjectCardProps>',
but 'ComponentInstance<TaskRow.Type, *>' was expected.
```

Passing a string where a typed component key is required:

```text
WrongComponentKey.kt:7:22 Argument type mismatch: actual type is 'String',
but 'TaskId' was expected.
```

The compiler supplies the source location and expected/received shape. Public documentation must place the valid pattern next to generated KSP diagnostics; custom diagnostics should add a stable searchable ID when generation rejects a declaration before ordinary type checking can run.

## Naming and generation assessment

The prototype uses one generated singleton per declaration and one nested nominal marker type per component:

- page `projectPage` → `ProjectPage` with nested `Parameters`;
- action `createTask` → `CreateTaskAction` with explicit command/result types;
- component `taskRow` → `TaskRow`, nested unconstructable `Type`, and region property `status`;
- no generated overload differs only by receiver magic or erased generic type.

Generation must sort canonical declaration IDs, reject naming/route/action collisions and produce byte-stable source for unchanged inputs. A component's nominal marker exists only to prevent cross-component binding; application authors never instantiate or persist it.

## Alternatives observed

- A raw function reference carries a Kotlin function signature but not route templates, generated URL encoding, region ownership, stable protocol ID or key requirements.
- A single `Ref<T>` makes page/action/region misuse possible whenever payload types coincide.
- String routes and CSS targets are easy to escape into but lose compiler refactoring and active-page target validation.
- Encoding rendered instances as a Compose-style receiver/state scope adds unfamiliar lifecycle semantics that this problem does not need.

The main cost of the nominal model is generic type verbosity in diagnostics and generated API signatures. Generated singleton names and nested marker types keep that cost out of normal call sites.

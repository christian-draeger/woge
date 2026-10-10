# Typed action descriptors

An action is a typed server entry point for a form command. It has a stable public ID, an ordinary
URL and a direct Kotlin call. It is not a client-selected class or function name.

**Current scope:** descriptors, executors and package registries are implemented. Automatic POST
dispatch through the host adapters is still part of [#28](https://github.com/christian-draeger/woge/issues/28);
bounded form-field decoding follows in [#29](https://github.com/christian-draeger/woge/issues/29).
Declaring an action does not install an endpoint or a security policy.

```kotlin
data class CreateTask(val title: String)

@WogeAction("create-task")
suspend fun createTask(command: CreateTask, context: RequestContext): PageResult {
    // Authorize and save the task in your application.
    return redirect(applicationUrl("/tasks"))
}
```

KSP generates `CreateTaskAction`. Its ID stays `create-task` even if the Kotlin function is renamed.
Its `url` is `/woge-actions/create-task`. Use it with normal HTML:

```kotlin
form(attributes = {
    attribute("method", "post")
    url("action", CreateTaskAction.url)
}) {
    input(attributes = { attribute("name", "title") })
    button { text("Create task") }
}
```

This form still needs an explicitly registered POST endpoint and the host's CSRF protection.
After decoding and security checks, the server calls
`CreateTaskAction.execute(PageRequest(command, context))`. The command type must match. The context
contains copied host authentication facts; the action still makes its own domain authorization decision.

Each package gets a `wogeActions: ActionRegistry` listing only its generated descriptors. Lookup
does not execute code. To combine packages, construct an `ActionRegistry` from their `actions`
lists; duplicate IDs are rejected even across modules.

Commands are non-null data classes with `val` properties. Supported fields are strings, integers,
long integers, booleans, UUIDs, enums, value classes wrapping these values and lists of scalar values.
Nullable fields are supported. Parsing policies and size limits belong to the form decoder, not
the descriptor.

The function must be top-level, suspend, non-generic, and take exactly `(command, RequestContext)`.
Its declared return type is `PageResult`: normal HTML, a redirect or a controlled failure.
Woge reports unsupported declarations with `WOGE-ACTION-001` through `WOGE-ACTION-007`, including
duplicate IDs and generated names.

The [compiled example](../../examples/m1-api-corpus/src/main/kotlin/dev/woge/examples/m1/OpenProject.kt)
uses a typed page route for its redirect and a generated action for its native form.
The design is recorded in [ADR 0052](../adr/0052-typed-action-executors-and-registry.md).

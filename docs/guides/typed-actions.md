# Typed action descriptors

An action is a typed server entry point for a form command. It has a stable public ID, an ordinary
URL and a direct Kotlin call. It is not a client-selected class or function name.

Descriptors, executors, package registries and explicit POST bindings are implemented.
Bounded form-field decoding follows in [#29](https://github.com/christian-draeger/woge/issues/29).
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

## Register a POST endpoint

Each host's `handlers.action(executor, input, securityContexts)` uses the same `ActionExecutor`
contract. Pass your application-owned typed input decoder and a context factory that translates
the host's authentication and CSRF decisions:

```kotlin
// Spring WebFlux: createTaskInput is a WebFluxPageInput<CreateTask>.
val action = handlers.action(CreateTaskAction, createTaskInput, securityContexts)
coRouter { POST(CreateTaskAction.path, action::handle) }

// Spring MVC: createTaskInput is a SpringMvcPageInput<CreateTask>.
SimpleUrlHandlerMapping(
    mapOf(CreateTaskAction.path to handlers.action(CreateTaskAction, createTaskInput, securityContexts)),
    0,
)

// Ktor: createTaskInput is a KtorPageInput<CreateTask>.
val action = handlers.action(CreateTaskAction, createTaskInput, securityContexts)
routing { post(CreateTaskAction.path) { action.handle(call) } }
```

The context factory is **required**, even if the surrounding handlers use default page contexts.
Those defaults only support safe page methods. Establish the host's security policy first, reject
missing or invalid CSRF verification at ingress, and only then supply `RequestContext`. Never trust a
submitted principal or CSRF-status field. Decode runs after context creation; domain authorization
still happens in the action.

The action binding accepts only POST. A direct call using another method returns 405 with
`Allow: POST`, before creating a context or reading the command. The existing page response mapper
handles redirects, HTML, controlled failures, cancellation and configured failure pages. Unknown
action URLs use the host's normal not-found behavior; no wildcard reflective dispatcher is installed.

Input decoding is explicit until #29 lands. The adapter does not collect unbounded form fields or
infer a command from arbitrary JSON.

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

The existing `scaffoldDevSmoke` gate runs both Spring adapters and checks incremental action addition,
ID changes, duplicate-ID diagnostics, recovery and removal of obsolete registry output.

The [compiled example](../../examples/m1-api-corpus/src/main/kotlin/dev/woge/examples/m1/OpenProject.kt)
uses a typed page route for its redirect and a generated action for its native form.
The design is recorded in [ADR 0052](../adr/0052-typed-action-executors-and-registry.md).

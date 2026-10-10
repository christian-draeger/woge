# Typed action descriptors

An action is a typed server entry point for a form command. It has a stable public ID, an ordinary
URL and a direct Kotlin call. It is not a client-selected class or function name.

Descriptors, registries, explicit POST bindings and bounded URL-encoded form decoding are implemented.
Declaring an action does not install an endpoint or a security policy.

```kotlin
@Serializable
data class CreateTask(val title: String, val urgent: Boolean = false)

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
    attribute("accept-charset", "UTF-8")
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
contract. Apply Kotlin's serialization plugin in the application build (using the same version as
your Kotlin plugin), annotate the command with `kotlinx.serialization.Serializable`, and create a
decoder:

```kotlin
// build.gradle.kts
plugins { kotlin("plugin.serialization") version "2.4.10" }

// Application code
val createTaskForm = FormDecoder(CreateTask.serializer())
```

Pass the decoder's host binding and a context factory that translates the host's authentication
and CSRF decisions:

```kotlin
// Spring WebFlux
val action = handlers.action(CreateTaskAction, createTaskForm.webFluxInput(), securityContexts)
coRouter { POST(CreateTaskAction.path, action::handle) }

// Spring MVC
SimpleUrlHandlerMapping(
    mapOf(CreateTaskAction.path to handlers.action(CreateTaskAction, createTaskForm.springMvcInput(), securityContexts)),
    0,
)

// Ktor
val action = handlers.action(CreateTaskAction, createTaskForm.ktorInput(), securityContexts)
routing { post(CreateTaskAction.path) { action.handle(call) } }
```

The context factory is **required**, even if the surrounding handlers use default page contexts.
Those defaults only support safe page methods. Establish the host's security policy first, reject
missing or invalid CSRF verification at ingress, and only then supply `RequestContext`. Never trust a
submitted principal or CSRF-status field. Decode runs after context creation; domain authorization
still happens in the action.

The body must still be readable when decoding starts. A Servlet security filter that calls
`getParameter` can consume the form before Woge sees it. Such integrations need a bounded,
replayable request body or a verification path that leaves the body intact; the bindings do not
silently fall back to merged Servlet parameters. End-to-end native Spring Security integration
is part of #30, not supplied automatically by this decoder.

The action binding accepts only POST. A direct call using another method returns 405 with
`Allow: POST`, before creating a context or reading the command. The existing page response mapper
handles redirects, HTML, controlled failures, cancellation and configured failure pages. Unknown
action URLs use the host's normal not-found behavior; no wildcard reflective dispatcher is installed.

The decoder reads only the form body, never query parameters. It accepts
`application/x-www-form-urlencoded` with UTF-8 (the default for these forms). JSON, multipart and
other charsets return 415. File uploads have a separate planned API.

## Form values and errors

The serializer supplies field names (`@SerialName` is supported) and default values. Commands must
be flat: nested objects and maps are not supported.

| Input | Behavior |
| --- | --- |
| String | Keeps text, including empty strings and spaces; `+` means a space and `%2B` means a plus |
| Number | Decimal, locale-independent, within the Kotlin type's range; no commas, NaN or infinity |
| Boolean | Exactly `true` or `false`; use `value="true"` and a `false` default for a checkbox |
| Enum | Lowercase serialized name with dashes: `IN_PROGRESS` becomes `in-progress` |
| Nullable scalar | Missing becomes null unless a declared default exists; empty text becomes null |
| Repeated list | `tag=a&tag=b` becomes `listOf("a", "b")`, in order; absent lists use a default or an empty list |
| Missing property with a default | Uses the command's declared default |
| Missing required scalar | `FormFieldError(field, MISSING)` |
| Invalid scalar or list element | `FormFieldError(field, MALFORMED)` |
| Repeated scalar | `FormFieldError(field, REPEATED)`, even if values are identical |
| Unknown field | `FormFieldError(field, UNKNOWN)` by default |

The descriptor also supports scalar value classes. UUIDs need an application-provided string
serializer; Kotlin serialization does not supply one for `java.util.UUID`. Custom serializers must
honor their descriptors; implementation exceptions propagate instead of becoming input errors.

For host-owned extra fields such as a verified CSRF token, use
`FormDecoder(CreateTask.serializer(), unknownFields = UnknownFormFields.IGNORE)` explicitly.
Ignored fields still count toward limits. This option does **not** verify the token.

Malformed fields or invalid URL encoding return 400 through the automatic bindings; the action is
not invoked. `FormDecoder.decode(bytes)` returns `FormResult.Decoded(command)` or
`FormResult.Rejected(problem)` for application-owned handlers. `FormProblem.Fields.errors` contains
all field errors, without submitted values. Use the request-owned form values when rendering a
validation response; the full native validation flow is covered by #30.

## Request limits

Defaults are 64 KiB encoded body bytes, 128 field occurrences (including repeated/ignored fields),
256 decoded UTF-8 name bytes and 16 KiB decoded UTF-8 value bytes. Thresholds are inclusive.
Invalid percent escapes or UTF-8, empty/control-character names and empty field segments are rejected.

Override only the budgets your endpoint needs:

```kotlin
val createTaskForm = FormDecoder(
    CreateTask.serializer(),
    limits = FormLimits(bodyBytes = 32 * 1024, fieldCount = 16, valueBytes = 8 * 1024),
)
```

All three adapters read fixed-size chunks and reject the first excess input with 413, stopping or
cancelling further reads. `FormProblem.LimitExceeded` identifies the limit and configured threshold,
not the payload. For a custom transport, create `decoder.body()`, feed chunks through `accept`, stop
as soon as it returns false, then call `decoder.decode(body)` once. Do not buffer the entire body
before applying limits. Host transport and security-filter limits remain additional protections.

## Descriptor rules

Each package gets a `wogeActions: ActionRegistry` listing only its generated descriptors. Lookup
does not execute code. To combine packages, construct an `ActionRegistry` from their `actions`
lists; duplicate IDs are rejected even across modules.

Commands are non-null data classes with `val` properties. Supported fields are strings, characters,
Kotlin numeric types, booleans, UUIDs, enums, value classes wrapping these values and lists of scalar values.
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
Form policies and request budgets are recorded in
[ADR 0053](../adr/0053-bounded-native-form-decoding.md).

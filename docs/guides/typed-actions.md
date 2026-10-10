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
submitted principal or CSRF-status field. The Woge handler reads its input after context creation;
domain authorization still happens in the action.

The body must still be readable when decoding starts. A Servlet security filter that calls
`getParameter` can consume the form before Woge sees it. Such integrations need a bounded,
replayable request body or a verification path that leaves the body intact; the bindings do not
silently fall back to merged Servlet parameters. Spring Security integration is not installed
automatically by this decoder. WebFlux's default CSRF token resolver
also collects form data, even when a token header is present. For a header-based endpoint, explicitly
configure a header-only Spring Security token resolver so the body remains readable. This is not
a replacement for hidden-field CSRF on JavaScript-free forms.

For native forms, the real Spring Security test configurations demonstrate a body-once integration:

1. A filter scoped to the action POST uses `springMvcSubmission()` or `webFluxSubmission()` to read
   bounded input, and stores the immutable submission on the request. Invalid encoding and budget
   failures stop at this boundary.
2. Spring Security checks the header or exactly one `_csrf` value from that submission. Query
   parameters do not supply a token. Its normal token generation and verification stay in place.
3. After authentication and CSRF verification, an explicit `SpringMvcPageInput` or `WebFluxPageInput`
   returns the saved submission. Woge creates the security context and runs the same action or
   validation renderer; neither can execute in the preparation filter.

Allow the host-owned `_csrf` field explicitly with `UnknownFormFields.IGNORE`; it still consumes
the same budgets. The application owns this filter and its security policy, not Woge.
See the [MVC configuration](../../adapters/woge-spring-mvc/src/test/kotlin/dev/woge/spring/mvc/SpringSecurityFormTest.kt)
and [WebFlux configuration](../../adapters/woge-spring-webflux/src/test/kotlin/dev/woge/spring/webflux/WebFluxSecurityFormTest.kt).
These are executable fixtures, not an automatically installed security starter.

`WebFluxRequestContextFactory.create` suspends: a factory can await
`request.principal().awaitSingle()` without blocking the event loop. Configure authentication and
CSRF first; a principal alone does not prove CSRF verification. Explicit factory implementations
must declare `suspend fun create`. This pre-beta API change is recorded in
[ADR 0055](../adr/0055-suspending-webflux-security-context.md).

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
all field errors, without submitted values.

### Render native field errors

Use `withFormValidation` when a native POST should return an HTML validation page instead of a
bodyless parsing failure:

```kotlin
val validation = PageUseCase<FormValidation> { request ->
    // Authorize this page too; parsing failure is not permission to view it.
    htmlPage(ResponseMetadata(status = ResponseStatus.BAD_REQUEST)) {
        actionForm(CreateTaskAction) {
            input(attributes = {
                attribute("name", "title")
                attribute("value", request.input.values.first("title").orEmpty())
            })
            request.input.errors.forEach { error -> p { text("${error.field}: ${error.code}") } }
            button { text("Create task") }
        }
    }
}
val nativeAction = CreateTaskAction.withFormValidation(validation)
val handler = handlers.action(nativeAction, createTaskForm.webFluxSubmission(), securityContexts)
coRouter { POST(CreateTaskAction.path, handler::handle) }
```

MVC uses `springMvcSubmission()` and Ktor uses `ktorSubmission()` in the same binding. The submitted
text remains request-owned and bounded. Select fields explicitly; never echo passwords or CSRF
tokens. Normal DSL text and attribute escaping still apply. Invalid encoding and oversized input
expose no partial text and do not invoke the validation renderer.

A valid command runs the original action with the original security context. Business-rule errors
remain action-owned and can return the same page renderer. On success, use `redirect(canonicalUrl)`
for a 303 followed by GET, not a POST-preserving 307/308. Rendering a validation page directly leaves
the browser on a POST response; a workflow that needs redirect-after-validation must explicitly
manage short-lived state.

The shared real-HTTP and Chromium tests verify a successful mutation followed by a canonical GET
and refresh, without repeating the mutation. Browser tests run with JavaScript disabled and enabled
on MVC, WebFlux and Ktor, including validation and domain rejection. Their security facts are
explicit test fixtures. Separate real Spring Security filter-chain tests verify authentication,
header and hidden-field CSRF rejection and domain authorization on both Spring adapters. They also
reject repeated tokens, query-only tokens, malformed encoding and excessive input. This foundation
does not complete #30: actual enhanced-submission parity is still pending.

The action bindings recognize an explicit `Accept: application/vnd.woge.patch-stream; version=1`.
For a successful application-owned 303, they return a bodyless 200 with `Woge-Navigate` instead.
The opt-in browser client then loads that canonical URL with GET, without repeating the mutation.
Native requests, external redirects and method-preserving 307/308 responses retain their behavior.
The all-host TCK verifies both paths and exact mutation counts. Enhanced field-error presentation
remains follow-up work. See
[action-form enhancement](fallback-client-installation.md#opt-in-to-action-form-enhancement).

## Update typed regions after an action

Return `actionRegionUpdates` when an enhanced form should update part of the current page instead
of navigating. Use the generated region descriptors that also render the original HTML:

```kotlin
val page = PageIdentity(activePageEpoch, applicationIdentitySecret)
val summary = TaskSummaryRegion.target(page)
val status = TaskStatusRegion.target(page)

return actionRegionUpdates(
    fallback = applicationUrl("/tasks"),
    interaction = activeInteractionSequence,
) {
    replace(summary, updatedTasks, revision = summaryRevision)
    replace(status, "Task saved", revision = statusRevision)
}
```

`TaskSummaryRegion` and `TaskStatusRegion` are generated from your `@WogeRegion` functions;
their input types must match. Use the same page identity and region keys as the current document.
Submit or otherwise track the active revisions and interaction sequence explicitly. Their initial
defaults only describe a region that has not yet been updated; they do not automatically synchronize
with the browser. These fields never replace authorization.

An ordinary POST gets a 303 to the canonical fallback URL. An opted-in enhanced POST gets the existing
patch stream, with replacements in declaration order and `Cache-Control: no-store`. Render the
success text into the document-owned status region referenced by your form. Woge does not invent
an extra announcement or move focus.

Every region renders through the safe DSL before the HTTP response starts. Duplicate targets,
mixed page epochs, more than 128 replacements, invalid protocol payloads or a rendering error reject
the whole prepared result. This prevents sending a successfully rendered subset, but does not
undo a mutation that your application already committed. Network delivery can still fail partway;
never automatically repeat the POST.

The shared JVM HTTP tests and optional Chromium flows exercise native and enhanced two-region
updates on all three hosts, including refresh without mutation replay. This is a Replace-only
foundation for #33, not its complete collection-update API. See
[ADR 0057](../adr/0057-prepared-typed-action-region-updates.md).

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
Its declared return type is `PageResult`: normal HTML, a redirect, prepared typed region updates
or a controlled failure.
Woge reports unsupported declarations with `WOGE-ACTION-001` through `WOGE-ACTION-007`, including
duplicate IDs and generated names.

The existing `scaffoldDevSmoke` gate runs both Spring adapters and checks incremental action addition,
ID changes, duplicate-ID diagnostics, recovery and removal of obsolete registry output.

The [compiled example](../../examples/m1-api-corpus/src/main/kotlin/dev/woge/examples/m1/OpenProject.kt)
uses a typed page route for its redirect and a generated action for its native form.
The design is recorded in [ADR 0052](../adr/0052-typed-action-executors-and-registry.md).
Form policies and request budgets are recorded in
[ADR 0053](../adr/0053-bounded-native-form-decoding.md).
Native validation rendering is recorded in
[ADR 0054](../adr/0054-native-form-validation-boundary.md).

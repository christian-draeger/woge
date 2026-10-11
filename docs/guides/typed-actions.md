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

Each host's `handlers.action(executor, input)` uses the same `ActionExecutor` contract and applies
the built-in same-origin request check by default. Pass a `securityContexts` factory only when your
application has a different security policy. Apply Kotlin's serialization plugin in the application build (using the same version as
your Kotlin plugin), annotate the command with `kotlinx.serialization.Serializable`, and create a
decoder:

```kotlin
// build.gradle.kts
plugins { kotlin("plugin.serialization") version "2.4.10" }

// Application code
val createTaskForm = FormDecoder(CreateTask.serializer())
```

Pass the decoder's host binding. These ordinary bindings need no hand-written context:

```kotlin
// Spring WebFlux
val action = handlers.action(CreateTaskAction, createTaskForm.webFluxInput())
coRouter { POST(CreateTaskAction.path, action::handle) }

// Spring MVC
SimpleUrlHandlerMapping(
    mapOf(CreateTaskAction.path to handlers.action(CreateTaskAction, createTaskForm.springMvcInput())),
    0,
)

// Ktor
val action = handlers.action(CreateTaskAction, createTaskForm.ktorInput())
routing { post(CreateTaskAction.path) { action.handle(call) } }
```

The default factory accepts an unsafe request when its `Origin` matches the request's scheme and
host. If Origin is absent or `null`, `Sec-Fetch-Site: same-origin` is accepted instead. A mismatched
Origin or missing same-origin evidence receives 403 before form decoding. Safe GET, HEAD and OPTIONS
requests do not require CSRF verification. This check does not authenticate the visitor or authorize
the action.

If your application uses Spring Security CSRF tokens, another token policy or authentication,
provide the host's `WebFluxRequestContextFactory`, `SpringMvcRequestContextFactory` or
`KtorRequestContextFactory`. Never trust a submitted principal or CSRF-status field. The Woge handler
reads input only after the context factory returns; domain authorization still happens in the action.

Behind a TLS-terminating reverse proxy, configure the trusted public origin. Spring Boot uses
`server.forward-headers-strategy=framework`; Ktor applications can install `XForwardedHeaders`.
Trust forwarded headers only from your own proxy, so the request origin matches the URL seen by the
browser without letting arbitrary clients choose it.

Enhanced forms also send a fresh random UUID in `Woge-Request-Identity`. Every action adapter makes
it available as `request.context.mutationIdentity`, separately from the host's trace ID. A malformed
or repeated header returns 400 before reading the command. Native forms can omit it; their command
fields do not change. Diagnostics redact the identity.

This does **not** make a mutation idempotent: until an explicitly scoped reservation store is
configured, duplicate permitted requests can still change data twice. The client never retries a
POST, and domain authorization is still required on every call. See
[ADR 0063](../adr/0063-mutation-request-identities.md).

### Reserve a mutation explicitly

`MutationReservationStore` is an optional infrastructure port, not part of your domain interface.
Supply a durable implementation with atomic scope/identity reservation and lease-fenced resolution.
After current CSRF and domain authorization, construct a `MutationReservationRequest` with:

- a trusted scope including the action, subject and resource;
- the request identity and a SHA-256 fingerprint of your canonical command;
- a fixed identity expiry and a later retention deadline, established in trusted state or signed context.

Never derive the scope or renew the expiry from untrusted request fields. `reserve(request, now)`
returns `Acquired(lease)` only once. Existing states distinguish ongoing work, completed work,
rejected work, an ambiguous commit, an expired identity and a conflicting command or replay window.
None permits repeating the mutation automatically.

After explicit domain commit facts are known, `resolve(request, lease, resolution, now)` records
`COMPLETED`, `REJECTED` (no commit), or `AMBIGUOUS`. A lost response, exception, crash or cancelled
request may have committed; never release that reservation for another execution. Storage errors
propagate. Your transaction design coordinates durable domain state and the reservation; Woge
cannot infer commit from a 200/400/500 response or safely cache streamed HTML for a future document.

Native success remains POST–redirect–GET: refreshing the GET never repeats a mutation. A deliberate
native resubmit needs your issued replay identity/window. Enhancement sends the UUID header but
still never retries a POST or treats a completed reservation as authorization.

The shared matrices `WOGE-CSRF-001`, `WOGE-AUTH-001` and `WOGE-REPLAY-001` run on MVC, WebFlux and
Ktor. They cover native and enhanced tokens, missing/invalid/expired-session rejection, authorization
on duplicates, completed/rejected/ambiguous reservations, changed-command conflicts, and exact
mutation counts. Spring tests use real Spring Security; Ktor's test explicitly supplies a bounded
form and session-token policy. The fixture store and session-expiry route are test-only.

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
other charsets return 415. File uploads use the separate
[native multipart API](native-multipart-uploads.md): `MultipartSubmission<Command>`, a generated
upload descriptor, `multipartActionForm` and the decoder's host-specific multipart binding.

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
also covers actual enhanced submissions: field errors keep the same status and values, while
successful updates and refresh never repeat a mutation.

The action bindings recognize an explicit `Accept: application/vnd.woge.patch-stream; version=1`.
For a successful application-owned 303, they return a bodyless 200 with `Woge-Navigate` instead.
The opt-in browser client then loads that canonical URL with GET, without repeating the mutation.
Native requests, external redirects and method-preserving 307/308 responses retain their behavior.
The all-host TCK verifies both paths and exact mutation counts. See
[action-form enhancement](fallback-client-installation.md#opt-in-to-action-form-enhancement).

To run the optional real-browser adapter contracts, install the client's npm dependencies and
Playwright Chromium, then build the client before running the tests:

```shell
npm --prefix client/woge-fallback-client run build
WOGE_NATIVE_BROWSER_SCRIPT="$PWD/client/woge-fallback-client/scripts/test-native-forms.mjs" \
  ./gradlew :woge-spring-mvc:test :woge-spring-webflux:test :woge-ktor:test --tests '*AdapterTckTest'
```

These tests load the built bundle; a source checkout alone is not enough. Normal JVM checks
without this explicit environment variable still require no Node installation.

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
updates on all three hosts, including refresh without mutation replay.
The [reference task board](../../examples/reference-application/README.md#try-a-multi-region-action)
uses the same builder to update its count, list, ordering fields and success status from one mutation.
Its ordinary form keeps visible controls outside the replaced regions, preserving focus without
an additional state-restoration layer. Replace is sufficient for this workflow; collection Append
and Remove remain available in the semantic Patch IR without adding a second action-builder API. See
[ADR 0057](../adr/0057-prepared-typed-action-region-updates.md).

## Share accessible field and form errors

Create field descriptors once from your decoder. Reuse the same descriptor when assigning an error
and rendering its field. Names come from the generated serializer, not reflective property access:

```kotlin
val title = createTaskForm.field(CreateTask::title, FormElementId.of("task-title"))
val summary = FormElementId.of("task-errors")
val errors = FormErrors(
    submitted.values,
    listOf(FormError(title, "Enter a task title"), FormError(null, "Check the task details")),
)
```

For `@SerialName("task-title")`, pass `serializedName = "task-title"` to `field`. A wrong command
property does not type-check; an unknown submitted name fails when the descriptor is created.
Translate decoder error codes into your application's safe messages. Business-rule errors use the
same model. Always authorize the renderer and run server validation for every submission.

The form stays ordinary HTML:

```kotlin
actionForm(CreateTaskAction, attributes = {
    data("woge-action", "")
    data("woge-status", "task-status")
    data("woge-alert", "task-alert")
    data("woge-error-summary", summary.value)
    data("woge-failure-message", "Check the result before submitting again.")
}) {
    formErrorSummary(errors, summary, "Check the task")
    input(attributes = {
        formField(title, errors)
        attribute("value", errors.value(title).orEmpty())
    })
    formFieldErrors(title, errors)
    button { text("Create task") }
}
```

The field helper adds its name, ID and error references. Pass `describedBy = listOf(hintId)` to
retain application help text. Text preservation is explicit: do not add the value attribute for
passwords or CSRF tokens. `FormValues.EMPTY` is available for the initial form.
The summary is focusable and links to invalid fields; it is not a live region.

Use the same typed form region to render a native 400 document and enhanced replacements:

```kotlin
val nativePage = htmlPage(ResponseMetadata(status = ResponseStatus.BAD_REQUEST)) {
    region(formTarget, errors, elementName = "section")
}
return actionValidationUpdates(nativePage, summary) {
    replace(formTarget, errors, revision = currentFormRevision)
}
```

Here `formTarget` comes from your `@WogeRegion` form function and takes `FormErrors<CreateTask>`.
The surrounding native document still needs your normal HTML head, title and other page content.
Native requests receive that HTML with status 400. Enhanced requests receive prepared patches with
the same status, then focus the named summary. No extra alert or success announcement is added.
The referenced status/alert elements still belong in your document shell for other action outcomes.
Supply the active interaction sequence and revisions as for other region updates.
See [ADR 0058](../adr/0058-shared-accessible-form-errors.md).

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

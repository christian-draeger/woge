# Accessible native form components

Start with HTML you already know: a form posts to a URL, labels name controls, and the server returns
errors or redirects after saving. Woge's source-owned `form-components` recipe provides that HTML,
not a JavaScript form manager. It works with Spring Boot MVC/WebFlux and Ktor.

## Start with a label and input

Install the plain-CSS recipe using the [component registry](component-registry.md):

```sh
./gradlew wogeComponentAdd --name=form-components --variant=css --preview
./gradlew wogeComponentAdd --name=form-components --variant=css
```

Add the reported `woge-host-spi` and `woge-ui-headless` dependencies at your Woge version. Load
`/assets/components/form-components.css` in the initial page head with the normal
[stylesheet helper](css-and-assets.md). No Node installation is needed.

Import the installed functions from your configured package. Inside your existing HTML writer:

```kotlin
val titleControl = FormControl(titleField, errors, description = "Give the note a short title.")
formLayout(saveAction) {
    formFieldRow(titleControl, "Title") {
        formInput(titleControl)
    }
    formButton("Save")
}
```

`formFieldRow` writes a label, your control, optional description and field errors. Its body is an
ordinary Kotlin block: `{ ... }` means “write these child elements.” `FormControl` holds a typed
field, existing server errors and optional presentation state. It does not validate the value.
`formLayout` delegates to `actionForm`: the action descriptor owns the native POST URL and encoding.
`formButton` is a native submit button by default.

Your [typed-action decoder](typed-actions.md) supplies `titleField`, for example
`decoder.field(NoteCommand::title, FormElementId.of("note-title"))`. A field is tied to the command
property, its serialized form name and a document ID. Reuse that field for validation messages.
For an initial page, supply `FormErrors<NoteCommand>(FormValues.EMPTY, emptyList())`; for an edit page,
put explicitly selected initial values in `FormValues.of(mapOf(titleField.name to listOf(note.title)))`.
For serialized aliases, pass the decoder's explicit `serializedName` as usual.

The executable [CSS contract fixture](../../registry/form-components/css/FormComponentsTest.kt)
shows complete field/action setup, real decoder submissions and all controls. The
[Tailwind fixture](../../registry/form-components/tailwind/FormComponentsTest.kt) runs the same contract.

## Add server validation and preserve values

After decoding or checking business rules, keep the submitted values in the existing `FormErrors`:

```kotlin
val errors = FormErrors(submission.values, listOf(FormError(titleField, "Choose another title.")))
val titleControl = FormControl(titleField, errors, description = "A unique note title.")
val summaryId = FormElementId.of("note-errors")
formLayout(saveAction) {
    formSummary(errors, summaryId, "Please check the form")
    formFieldRow(titleControl, "Title") { formInput(titleControl) }
    formButton("Save")
}
```

Return this HTML on the native validation response, normally HTTP 400. It does not execute a
mutation. The input gets the submitted value, unchanged apart from HTML escaping. The label's
`for` matches its ID; `aria-describedby` joins help and errors; invalid controls also have
`aria-invalid="true"` and `aria-errormessage`. Error text is escaped, not inserted as markup.

The non-empty summary has a heading, a focusable target (`tabindex="-1"`), native `autofocus` and
ordinary links to invalid fields. Form-wide errors have no invented field link. Without JavaScript,
browsers supporting native autofocus focus the summary; the links remain usable everywhere. Use
only one autofocus summary per document. Set `autofocus = false` when the page owns focus elsewhere.
An empty summary renders nothing. It is deliberately **not** a live region, avoiding duplicate alerts.

For enhancement, keep the same summary ID in the existing
`actionValidationUpdates(nativeResult, summaryId) { ... }` response. Woge's existing validation header
and focus behavior continue unchanged; the recipe installs no event handlers. See
[typed actions](typed-actions.md) for authorization, CSRF, decoding and native/enhanced response paths.

Select only values safe to redisplay. Never preserve passwords, hidden tokens or an entire request bag
indiscriminately. These text recipes intentionally have no password/hidden input variant: use the
native typed DSL for those controls and apply your application's explicit sensitive-value policy.

## Compose other native controls

| Function | HTML and composition |
| --- | --- |
| `formLabel`, `formDescription`, `formError` | Separate pieces when a field row is not the right layout |
| `formInput` | Native text/email/tel/url/number/date input via `FormInputType` |
| `formTextarea` | Escaped submitted text inside `textarea`, not a `value` attribute |
| `formCheckbox` | Checkbox plus label, optional description and errors; value defaults to `"true"` |
| `formRadioGroup` | `fieldset` and `legend`, native same-name radio inputs with individual labels |
| `formSelect` | Native select; wrap with `formFieldRow` for its label/description/errors |
| `formSwitch` | Native checkbox with `role="switch"`; Space and checked state remain browser-owned |
| `formButton` | Submit, plain button or reset; explicit attributes retain name/value/submitter overrides |
| `formLayout`, `formSummary`, `formSuccess` | POST form, linked validation summary, success status text |

Checkbox and switch already render their labels/help/errors; do not wrap them in another labeled
`formFieldRow`. Radio groups use their legend instead of a field label. Their summary link targets
the focusable group; each radio has a distinct `FormChoice.id` and shares the existing field's error
association. Keep all document IDs unique, including generated `-description` and `-error` IDs.

Choices are explicit `FormChoice(value, label, id, disabled)` values. Translate labels, not submitted
values. Checkbox membership and select options come from `errors.values.all(field.name)`; an absent
checkbox value stays unchecked, with no fabricated hidden fallback input. `formSelect(..., multiple =
true)` preserves repeated submitted choices. Radio selection uses the first submitted value. Only
render trusted choice options; an unknown submitted choice does not create a new option. A single
select with no matching option follows the browser's normal first-option behavior; include a trusted
empty-value placeholder when the field must start unselected.

## Advanced states and submitters

```kotlin
val titleControl = FormControl(
    titleField,
    errors,
    state = FormControlState(required = true, readOnly = true),
)
formFieldRow(titleControl, "Title") { formInput(titleControl) }
formButton("Save draft") {
    attribute("name", "intent")
    attribute("value", "draft")
}
```

Required, disabled and readonly are native Boolean attributes. Readonly text is submitted; disabled
controls are not. Checkbox, radio, switch and select reject readonly, because HTML has no such
native state for them. Choose disabled deliberately, or render a separate read-only text view.

`formLayout(pending = true)` and `FormButtonState(pending = true)` expose `aria-busy` and
`data-pending`, not custom submission logic. Pending does **not** automatically disable the
submitter: the existing enhanced client captures its name/value before temporary disabling.
`FormButtonState(disabled = true)` is explicit native disabling. Do not disable a named submitter
before capture and expect its value to be submitted. Use `FormButtonType.BUTTON` for non-submit
buttons; the default remains `SUBMIT`. Native `formaction`, `formmethod` and other allowed attributes
remain visible through the typed attribute block. Preserve the application's action/CSRF policy.

Use `formSuccess("Saved.")` for a visible `role="status"` confirmation. For enhanced announcements,
keep the status container in the page and update it through your normal region path; initial-page
status text is visible but not guaranteed to announce as a newly inserted live-region message.

## Plain CSS, Tailwind, RTL and localization

Every component exposes `data-woge-scope="woge-form-components"` and a `data-part` hook. Controls
also expose `data-required`, `data-invalid`, `data-disabled`, `data-readonly`; form/buttons expose
`data-pending`; confirmation exposes `data-success`. Native attributes remain the source of semantics.

The plain stylesheet uses logical properties, a deterministic `woge.components.form-components`
layer and `--woge-form-gap`, `--woge-form-radius`, `--woge-form-focus`. Both variants consume the
reference Tailwind theme's `--woge-surface`, `--woge-ink`, `--woge-muted`, `--woge-line`, `--woge-danger`
and `--woge-ok`, with useful fallback colors. Reference tokens, dark-mode and contrast overrides live
in [the Tailwind example theme](../../examples/tailwind-spring-boot/src/main/tailwind/tailwind.css).
Applications can override these ordinary custom properties without adopting Tailwind.

For Tailwind, install `--variant=tailwind` into a fresh target and scan your installed Kotlin source.
The registry declares `FormControl.kt` as the candidate asset; its utilities are complete static
strings, use logical spacing and require no custom JavaScript. See [Tailwind setup](tailwind.md).

Set `lang` and `dir="rtl"` on the document or an enclosing native element; pass translated labels,
descriptions, errors and summary titles as strings. The recipes do not assume English, add visual-only
asterisks or replace browser keyboard interactions. JVM fixtures cover translated RTL markup, label
and error associations, native radio/switch/selection behavior and focus targets. Continue the
[manual keyboard and screen-reader checklist](accessibility.md) on your supported browser/platforms;
HTML assertions do not claim to simulate an assistive technology.

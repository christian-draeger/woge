# ADR 0058: Share accessible form errors across native and enhanced responses

- Status: Accepted
- Date: 2026-10-10
- Decision owners: Woge maintainers
- Related issues: [#35](https://github.com/christian-draeger/woge/issues/35), [#30](https://github.com/christian-draeger/woge/issues/30)
- Refines: [ADR 0048](0048-document-owned-accessibility-announcements.md), [ADR 0057](0057-prepared-typed-action-region-updates.md)

## Context

Invalid native and enhanced submissions need the same messages and submitted values. Returning an
HTML error page to fetch does not update the current form. Announcing both an alert and a focused
error summary makes assistive technology repeat the error.

## Decision

Create a `FormField<Command>` from the decoder and a Kotlin command property. The serializer
validates its submitted name; an explicit alias is needed for `@SerialName`. This uses the property
name and generated serializer metadata, never reflective property access or constructor lookup.
The application supplies document IDs and reuses its field descriptors.

`FormErrors<Command>` owns an immutable list of field or form-wide errors and the existing bounded
`FormValues`. Messages are application-owned, client-safe text. Helpers render ordinary HTML:
field names and IDs, `aria-invalid`, `aria-describedby`, message paragraphs and a summary with
fragment links. Preserving a field's text remains an explicit value attribute; passwords and CSRF
tokens are never automatically echoed.

Extend prepared region results to own their native representation. Successful results still own
a 303 redirect. `actionValidationUpdates` owns a 400 HTML document, prepared replacements and one
typed summary ID. The adapters return that document natively or a no-store 400 patch stream with
`Woge-Validation` for an explicit enhanced request. Both representations vary by `Accept`.
The application authorizes the renderer and revalidates every POST. Neither parsing nor validation
rendering is a mutation.

The enhanced form explicitly names its summary with `data-woge-error-summary`. The client accepts
only that summary ID, consumes the entire valid stream, then focuses its `tabindex="-1"` element.
The summary must not be a live region or inside one. Validation writes no status text, alert text
or diagnostic failure event. Cancellation, stale patches and failed streams never focus a summary.
Success keeps focus; transport and authorization failures retain the existing alert policy.

## Alternatives considered

- **Insert an arbitrary HTML response:** rejected; use typed replacements and normal protocol checks.
- **Use both an alert and summary focus:** rejected; violates the single-outcome announcement policy.
- **Reflect over command properties:** rejected; the generated serializer already owns input names.
- **Copy submitted values into every input automatically:** rejected; sensitive fields need explicit handling.
- **Add focus to the Patch IR:** rejected; this is a submitted-form outcome, not a general patch operation.

## Consequences

Native and enhanced forms share one presentation model while HTML, HTTP 400 and normal links stay
visible. Application language, business-rule messages, authorization and transactions remain
application-owned. The client moves focus only after an explicit validation result.

Applications must keep field IDs, summary IDs and active target counters consistent with their
document. A native renderer remains a cold HTML document; its rendering can fail under the same
rules as other native pages. Enhanced replacements are prepared before sending any response.

## Follow-up

The shared adapter HTTP/Chromium contracts cover the same invalid submission with JavaScript off,
native JavaScript on and actual enhancement. Browser fixtures cover summary focus, no extra speech,
unconfigured focus targets and stale results. General preservation across arbitrary replacements
remains #36; this validation policy does not silently merge browser-edited state.

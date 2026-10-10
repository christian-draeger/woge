# ADR 0054: Keep native form validation in the page and action boundary

- Status: Accepted
- Date: 2026-10-10
- Decision owners: Woge maintainers
- Related issues: [#30](https://github.com/christian-draeger/woge/issues/30)
- Refines: [ADR 0053](0053-bounded-native-form-decoding.md)

## Context

A native form needs submitted text when a number or another field cannot be parsed. Turning all
such errors into bodyless 400 responses loses the information needed for a useful validation page.
The command must not execute until parsing succeeds, and a validation page is not exempt from
domain authorization.

## Decision

`FormDecoder.submission(body)` retains bounded submitted text only after the complete body passes
encoding and resource checks. `FormSubmission` contains the existing typed result and `FormValues`.
Applications select individual fields when rendering; the API has no operation to echo every
submitted value. String representations redact text. A malformed or oversized body exposes none
of its partial fields.

`ActionExecutor.withFormValidation(page)` adapts the existing action to a submission. Valid commands
invoke the original action with the same request context. Structured field errors invoke the
application's `PageUseCase<FormValidation>` instead. Encoding and budget errors remain controlled
400/413 failures, without running either application callback. Unsupported content types still
fail at ingress with 415.

The validation page owns its status, markup and authorization. Parsing success is not permission
to mutate, and parsing failure is not permission to view a protected validation page. Business-rule
validation remains inside the action, which can return the same normal HTML page result.

All adapters expose a submission input binding alongside their existing command-only binding.
Both share the same incremental reader; security-context factories remain mandatory for POST.
The existing response mapper handles validation HTML and redirects. No new response protocol or
parallel enhanced dispatcher is introduced.

`actionForm(descriptor)` writes an ordinary form with the descriptor URL, POST, URL encoding and
UTF-8. Named controls and CSRF fields remain visible DSL markup. Successful actions use the existing
303 redirect helper. Applications must not use 307/308 for a mutation's normal success response,
because those statuses preserve POST.

## Alternatives considered

- **Build commands from invalid fields anyway:** rejected; it obscures missing/malformed values.
- **Throw submitted text into exception messages:** rejected; logs must not expose form contents.
- **Store validation text in a server-wide session automatically:** rejected; lifetime, cleanup and
  redirect-after-validation behavior belong to the application's workflow.
- **Own a second enhanced action transport:** rejected; the same action and domain checks remain
  authoritative.

## Consequences

### Positive

- Field errors rerender through ordinary HTML without executing a mutation.
- All three adapters share the same behavior and bounded text snapshot.
- The action form's endpoint and encoding cannot drift from its descriptor.
- Applications can reuse normal page rendering for parsing and business-rule validation.

### Negative

- Applications must explicitly authorize validation pages and choose which values are safe to echo.
- A direct validation HTML response remains a POST response. Applications that require a
  redirect-after-validation must explicitly manage short-lived state rather than hiding it in Woge.
- This boundary alone does not provide a complete Spring Security integration or browser workflow.

## Follow-up

The shared real-HTTP TCK records successful mutations per server, follows the action's 303 target
with GET and refreshes it again. It verifies exactly one mutation after successful, unauthorized
and malformed submissions on all three hosts.

#30 remains open until a real mutation is exercised in a browser with JavaScript disabled,
native/enhanced outcomes agree, and Spring Security ingress is tested end to end. Multipart remains
#60.

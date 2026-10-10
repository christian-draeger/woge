# ADR 0056: Enhance opted-in native forms without replaying mutations

- Status: Accepted
- Date: 2026-10-10
- Decision owners: Woge maintainers
- Related issues: [#31](https://github.com/christian-draeger/woge/issues/31), [#30](https://github.com/christian-draeger/woge/issues/30)
- Refines: [ADR 0047](0047-canonical-failure-and-recovery-model.md), [ADR 0048](0048-document-owned-accessibility-announcements.md)

## Context

A browser POST may already have changed data when its response disappears. Automatically repeating
that POST as a native fallback can save twice. Enhancement must also preserve browser form rules:
which button was used, repeated fields, validation, encoding and navigation targets.

## Decision

Expose `installWogeActionForms(document, runtime)` as an explicit browser API. Importing the client
does not install listeners. Only forms marked `data-woge-action` are candidates. The effective
submitter action, method, encoding and target must describe a same-origin, URL-encoded UTF-8 POST
in the current window. Unsupported forms, image submitters and file controls remain native.

Create input with the browser's `FormData(form, submitter)` and encode it with `URLSearchParams`,
normalizing line endings like native URL-encoded forms. This preserves successful controls,
submitter fields, repeated values and application-owned `formdata` events. Browser validation runs
before the submit event; Woge does not replace it.

The request advertises the existing versioned patch media type. A successful patch response is
consumed by the existing runtime. A successful response may instead provide `Woge-Navigate` with
an HTTP(S), same-origin URL; Woge performs a normal GET navigation. Fetch redirects are rejected,
not followed implicitly, so a POST cannot be redirected and replayed behind the client's back.
This header is an explicit enhanced-response contract, not a change to native 303 responses.
The three host action bindings translate a same-application 303 into a bodyless 200 with this header,
`Cache-Control: no-store` and `Vary: Accept`. Only the explicit current-version patch media type selects
this representation. Page bindings, external redirects and method-preserving 307/308 redirects keep
their original behavior. `Woge-Navigate` is reserved response metadata, not an application header.

While a request is active, set `aria-busy` on its form and `aria-disabled` on its submitter. The
button stays focusable; repeated submit events for that form are prevented. Restore original
attributes on completion, failure or disposal. Disposal aborts owned requests and never retries.

Require document-owned `role="status"` and `role="alert"` elements, referenced by form attributes.
The application renders one success announcement through an ordinary patch. The client creates
no live regions and writes no success text. A failed response uses the form's explicit, safe
failure message in its alert and emits `woge:action-error` with a stable diagnostic code. Ignored
stale interactions and cancellation remain silent. Focus is never moved by this transport.

## Alternatives considered

- **Intercept every form:** rejected; ordinary browser behavior must remain the default.
- **Retry a failed POST through native submission:** rejected; the first mutation may have succeeded.
- **Build a parallel patch decoder:** rejected; reuse the validated runtime and recovery classifier.
- **Treat arbitrary returned HTML as an update:** rejected; patch framing and target checks are required.
- **Disable the actual submit button:** rejected; it removes focusability and changes successful controls.
- **Accept off-origin navigation or implicit fetch redirects:** rejected; explicit navigation has a narrow policy.

## Consequences

### Positive

- HTML forms remain useful with JavaScript disabled or unavailable.
- One request owns its busy state and cancellation; mutations are never replayed automatically.
- The npm and JVM asset distributions expose the same API, with no new dependency.

### Negative

- Applications must opt in and provide outcome regions and a failure message.
- An uncertain mutation outcome requires application-specific recovery, not an automatic resubmit.
- Multipart and image-coordinate submissions stay native until their separate requirements are implemented.

## Follow-up

The browser fixtures verify real HTTP patch responses, native no-JavaScript submission, form
semantics, focus, recovery and explicit navigation. The shared real-HTTP TCK and optional Chromium
flows verify native and enhanced navigation, authorization, validation status and exact mutation
counts on all hosts. [ADR 0057](0057-prepared-typed-action-region-updates.md) adds typed replacement
results and their all-host evidence. Enhanced field-error presentation still needs its parity
evidence before #30/#31 can close. Full collection updates and the reference application belong to #33.

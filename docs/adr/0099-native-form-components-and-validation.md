# ADR 0099: Compose source-owned native forms with the existing validation model

- Status: Accepted
- Date: 2026-10-11
- Decision owners: Woge maintainers
- Related issues: [#82](https://github.com/christian-draeger/woge/issues/82)
- Builds on: [ADR 0018](0018-hybrid-headless-and-source-owned-components.md), [ADR 0073](0073-native-first-headless-ui-primitives.md), [ADR 0098](0098-source-owned-component-registry.md)

## Context

Production forms need editable presentation, submitted values, associated errors and native keyboard
behavior. Typed actions and the accessible `FormField`, `FormValues` and `FormErrors` model already
belong to the framework-neutral host SPI. Moving that model into the headless module would invert
its core-only dependency boundary; copying it into recipes would create conflicting validation APIs.

## Decision

Keep those stable semantics in `woge-host-spi`; add no new headless form model. Extend its existing
field association helper with an overload for individual radio IDs and `aria-errormessage` on invalid
controls. Keep the original signatures. Extend the existing summary with an attribute overload so
source-owned presentation can opt into native `autofocus` without duplicating summary markup.

Publish one cohesive `form-components` registry item with explicit CSS and Tailwind variants:
field, label, description, error, text input, textarea, checkbox, radio group, select, switch, button,
action form layout, error summary and success feedback. A small recipe-owned `FormControl` bundles
the existing typed field/errors with description and native required/disabled/readonly states; it
is a presentation input, not another validation result. All markup uses the typed HTML DSL.

Text and choices render explicitly selected submitted values from `FormErrors.values`, including
unchecked checkboxes and repeated select values. Passwords and hidden security fields are not
preserved by the recipe. Applications still validate, authorize and apply CSRF policy server-side.

Validation pages use the existing focusable linked summary with native `autofocus`; applications
name that same summary in `actionValidationUpdates` for enhanced focus. Summaries remain non-live
regions. Success feedback uses the existing `liveRegion` primitive. Enhancement is unchanged: no
custom handlers, hidden inputs, control-name rewriting or synthetic switch/radio keyboard behavior.
Pending is `aria-busy` plus a theme hook, not automatic submitter disabling; the existing client owns
in-flight disabling after capturing the successful submitter. Explicit disabled state remains native.
Readonly is rejected on checkbox, switch, radio and select because HTML cannot express it there.

Both variants expose stable scope/part/state hooks and shared reference-theme tokens. Plain CSS uses
logical properties and `woge.components.form-components`; Tailwind uses complete static candidates
and its normal utilities layer. Neither requires Node or hydration for native forms. Each variant
ships compile-tested JVM contracts; a cross-variant test checks equivalent semantics after classes
are removed. Tests cover translated RTL labels, associations, fieldset/legend, native choices,
submitter values, readonly/disabled/pending/success and linked autofocus summaries.

## Alternatives considered

- New headless field/validation types: rejected; the existing public model already owns semantics.
- Separate registry item for every tag: deferred; a cohesive source bundle avoids dependency and style
  fragmentation while allowing applications to compose and edit individual functions.
- JavaScript-managed controls: rejected; native controls already provide keyboard and submission rules.

## Consequences

Applications add the declared host/headless dependencies and link styles in the initial page head.
They own recipe updates and translation strings, including summary titles. The recipe does not
translate messages or create business validation rules. Semantic fixtures are not substitutes for
manual screen-reader/browser audits, particularly native autofocus support and platform select UI.

## Follow-up

- Exercise forms with real assistive technologies and platform-native select controls in release audits.
- Add advanced date/file/combobox workflows only when their native fallback and validation contracts are clear.
- Keep source hashes, immutable releases and all variant contracts aligned when extending the bundle.

# Accessibility: keyboard, focus and asynchronous outcomes

Woge updates HTML, not just pixels. People must be able to understand and finish the same task
with a keyboard, with a screen reader, without JavaScript, and after an enhancement fails.
Start with a useful HTML page. Add enhancement only when it preserves that baseline.

## Choose native elements first

Before writing a custom widget, try these browser features. They already supply keyboard behavior
and semantics, and work without an application JavaScript bundle.

| Task | Native baseline | Woge helper or guide |
| --- | --- | --- |
| Go somewhere | `<a href>`; including a real destination for dialog content | `dialogLink` in [UI primitives](ui-primitives.md) |
| Perform an action | `<form method="post">`, labelled controls and a submit `<button>` | [Typed actions](typed-actions.md) |
| Search or filter | `<form method="get">` with a labelled input | [Typed routes](typed-routes.md) |
| Pick one or several values | `<select>`, radio buttons, checkboxes; group with `<fieldset><legend>` | [HTML elements](html-elements.md) |
| Show or hide details | `<details><summary>` | `disclosure` |
| Show extra information above the page | A button with `popovertarget` and an element with `popover` | `popoverButton` and `popoverPanel` |
| Block interaction until a panel is dismissed | `<dialog>` opened with `showModal()`, a named title and a close button | `modalDialog` with the optional dialog module |
| Show tabular data | `<table><caption>`, column headers and row headers with `scope` | Reference project dashboard |
| Report an asynchronous result | An initially empty `role="status"` or `role="alert"` element | `liveRegion` |

A popover is not a modal: it does not trap focus or make the page inert. A dialog must have a real
fallback page. Do not make an ordinary `div` into a button, a table into a grid widget, or links into
fake tabs merely to style them. CSS can change their appearance without changing their behavior.
If native HTML cannot express your interaction, document why, name its keyboard pattern, and test
all keys, focus boundaries and the native fallback before publishing the custom widget.

## One asynchronous outcome, one announcement

[ADR 0048](../adr/0048-document-owned-accessibility-announcements.md), from issue #120, owns the
announcement policy. The patch runtime stays silent. The document owns announcements; the page
must contain the live-region element before its text changes. Do not announce transport events.

| Situation | What the user gets |
| --- | --- |
| Deferred page content is loading | Ordinary visible loading text. No automatic live region or busy flag |
| Deferred content finishes | Content replaces the loading text. No extra announcement or focus move |
| A user submits an enhanced form | `aria-busy="true"` on the form and `aria-disabled="true"` on its submitter until it settles; no progress speech |
| The mutation succeeds | One short polite status, such as “Task added”; keep focus |
| Server validation fails | A focused error summary with links to the fields; no simultaneous alert or status |
| An action fails but the page still works | One short `role="alert"` explaining the outcome and next step; keep controls editable |
| Unrequested live data changes | No speech by default. Show a visible notice and a link if useful |
| The user explicitly chooses to follow live data | An optional polite summary, not every changed row |
| A stale or superseded update is ignored | No content, focus or announcement change |
| Recovery or an action loads another page | The normal document title/content; no competing live announcement |

Do not use `aria-live="assertive"` for success or loading. Do not mark the error summary as a live
region: focusing it already presents the error. Background patches must not repeat an action's
success message. The reference board's activity notice is deliberately **not** a live region.

The form adapter requires pre-existing status and alert elements and a configured failure message
before it enhances a POST. If it cannot safely enhance, it leaves the form native. A failed POST is
not replayed automatically: the server may already have performed it.

## Test focus separately from speech

Focus tells people where their next action belongs. Speech tells them what happened. Test both,
but do not use one as a substitute for the other.

- Validation focuses only the configured `tabindex="-1"` summary after the complete valid response.
  Tab reaches an error link; Enter goes to its field. Fields have `aria-invalid` and an
  `aria-describedby` reference to the same visible error. See [ADR 0058](../adr/0058-shared-accessible-form-errors.md).
- Success, deferred completion and live updates do not steal focus. Preserve an edited control
  only under the explicit [browser-owned state](browser-owned-state.md) contract.
- An enhanced action navigation performs an ordinary same-origin GET. Its focus baseline is a
  newly loaded document, not a validation summary or an arbitrary updated region.
- A modal starts focus inside, keeps the background inert, closes with Escape or its close button,
  then returns focus to its opener (including a replacement opener after a patch).
- A popover uses the browser's non-modal keyboard behavior. Escape closes it and returns focus to
  its button. Disclosure summaries work with Enter and Space.

## Shared component contract

Every shipped primitive and every future published component family must pass the same contract:

1. Useful native HTML and a working no-JavaScript journey, with understandable failure/recovery.
2. A page language and title, a main landmark, meaningful headings, named controls and links,
   unique IDs, valid label/error references, and real table captions/headers where applicable.
3. Keyboard reachability, visible focus, correct validation focus and overlay dismissal/return.
4. The outcome policy above, with no duplicate or stale live-region writes.
5. Reflow at 320 CSS pixels, usable controls, light/dark contrast and reduced-motion behavior.
6. Strict CSP and Trusted Types compatibility without inline handlers or weaker production headers.

The current public UI module ships disclosure, dialog, popover and live-region primitives. The
reference pages exercise those four and application-owned forms, summary cards, activity lists and
data tables. The source-owned registry/component-family release model is still planned, not an
already audited catalog. A new family must add its native and enhanced fixtures to this contract;
styling recipes do not get an exemption. Customization can introduce new failures, so applications
must rerun the contract with their own text, CSS, widgets and data.

### Small screens and motion

Use the viewport meta tag so 320 CSS pixels means 320 CSS pixels on a phone. Keep skip links and
focus outlines visible. Let long form values and table text wrap. When a genuinely two-dimensional
table needs horizontal scrolling, keep that scrolling inside a named, keyboard-reachable region,
not on the entire page. Do not hide columns or remove headers to make a screenshot fit.

Use `prefers-reduced-motion` to remove optional transitions and animations. Never make motion the
only way to identify a changed value. The reference dashboard animates only a border color when
motion is allowed; its reduced-motion test checks zero animation and transition duration.

## Automated checks

The existing Playwright projects and CI jobs run these checks; there is no extra browser matrix.
The browser package's axe dependency is test-only. Applications still need no Node.js toolchain.

| Evidence | What it checks |
| --- | --- |
| `browser-tests/accessibility.spec.mjs` | Deferred loading/completion and superseded patches stay silent and preserve focus |
| `browser-tests/action-accessibility.spec.mjs` | Counts live-region writes for success, failure, validation, stale results and navigation; independent validation/navigation focus tests |
| `browser-tests/actions.spec.mjs` and `state.spec.mjs` | Busy state, duplicate-submit protection, unsafe results, configured focus targets and browser-owned controls |
| `reference-browser-tests/accessibility.enhanced.spec.mjs` | Keyboard-only board journey, all primitives, axe WCAG A/AA and best-practice scans, reflow, dark/light contrast, reduced motion, silent live invalidation and complete-page recovery |
| `reference-browser-tests/accessibility.no-javascript.spec.mjs` | Keyboard-only native POST, disclosure/popover, help-page fallback and complete project data; the shared synchronous semantic/reflow checks |
| `reference-browser-tests/ui-primitives.*.spec.mjs` | Native dialog/popover behavior, focus return to a replaced opener and primitive state across patches |
| `scripts/test-native-forms.mjs` | Real adapter validation: native/enhanced messages, preserved values, field descriptions, summary focus and same-origin navigation |
| `development-browser-tests/development.spec.mjs` | Development overlay keyboard dismissal, usable controls during failure and recovery, focus/state handoff |
| `modules/woge-ui-headless/src/test/kotlin/dev/woge/ui/PrimitivesTest.kt` | Exact native markup, safe IDs, escaped names and the small optional dialog module |

The dense dashboard test adds 30 rows with long titles and 18 labelled fields to the actual reference
page. This is a CSS/semantic stress fixture, not a new domain feature: form dispatch is tested on the
real task board and adapter forms. Table captions and scoped headers remain intact, and every stress
field is reached and edited by keyboard at 320 pixels.

The shared helper is `client/woge-fallback-client/test-support/accessibility-contract.mjs`. Native
projects keep JavaScript disabled throughout: synchronous browser inspection checks labels, IDs,
landmarks and relationships; axe runs only in enhanced projects because its asynchronous injected
script cannot run with JavaScript disabled. No CSP policy is loosened to run the scanner.

From the repository root, after building the client and installing the pinned test browsers:

```shell
cd client/woge-fallback-client
npm ci
npm run build
npx playwright install chromium firefox webkit
npx playwright test action-accessibility.spec.mjs accessibility.spec.mjs
npx playwright test --config=playwright.reference.config.mjs accessibility
cd ../..
./gradlew referenceBrowserSmoke --console=plain
```

`referenceBrowserSmoke` runs shared journeys through WebFlux, MVC and Ktor. For a focused local run,
select `--project=chromium-enhanced --project=webkit-enhanced --project=chromium-no-javascript
--project=webkit-no-javascript` on the reference command. CI still runs all three engines.
WebKit keyboard tests use Option+Tab for native all-control navigation; macOS/Safari's plain Tab can
otherwise visit only text controls. This is a browser preference, not a custom focus controller.

A DOM mutation counter measures **announcement opportunities**, not actual speech. Axe cannot prove
keyboard usability, reading order, meaningful error messages or screen-reader behavior. Passing the
checks is necessary, not an accessibility certification. Inspect failure traces and screenshots in
the existing Playwright reports, then run the manual audit below.

## Manual audit checklist

Record browser/OS, screen reader and version, JavaScript mode, viewport/zoom, motion preference,
journey, expected/observed result and any issue links. Repeat after changing component CSS or content.
Use real keyboard and assistive technology, not only a DOM inspector.

- [ ] Start with JavaScript off. Read the project, load complete data, add a task, open help and
  submit invalid data. Outcomes, error messages and submitted values remain understandable.
- [ ] Repeat with enhancement and a blocked/disconnected patch request. Use the complete-page
  recovery control. Refresh after a successful POST; it does not perform the mutation again.
- [ ] Use only Tab/Shift+Tab, Enter and Space from page entry. The skip link appears on focus;
  controls and links have visible focus, meaningful names and a sensible order. No keyboard trap.
- [ ] With a screen reader (for example NVDA/Firefox or VoiceOver/Safari), navigate landmarks,
  headings, controls and tables. Confirm caption, row and column headers and reading order.
- [ ] Submit invalid data. Hear/read one error summary, follow every field link, find its description
  and correct the value. Do not hear both a live alert and the focused summary.
- [ ] Hold an action response. Busy state is understandable and the submitter remains focusable.
  Hear one success or failure result, never “loading”, every patch, then “done”.
- [ ] Receive repeated live updates while editing a draft. Focus and draft remain; unrequested
  notices are silent. If the page offers “follow updates”, opt in and check concise polite summaries.
- [ ] Open and close dialogs and popovers with the keyboard. Verify modal focus containment and
  inert background, non-modal popover behavior, Escape, close controls and return to the opener.
  Repeat while a patch replaces the opener or an ancestor. Check the no-JavaScript help destination.
- [ ] At 400% zoom on a 1280-pixel viewport (320 CSS pixels), check dashboard summaries, overlays,
  dense forms, long names and data tables. No page-wide horizontal scrolling or clipped buttons;
  any local table scroller is keyboard usable and retains its headers.
- [ ] Enable reduced motion before loading and during a session. No essential feedback disappears;
  optional animation stops. Check focus contrast, text contrast, dark mode and forced colors.
- [ ] Navigate after an enhanced action and after recovery. The new title/content is useful and
  focus follows the browser's normal new-document behavior, without competing live speech.

No real screen-reader or physical-device audit is claimed by the automated suite. Record those
manual results separately before making a release-level conformance claim.

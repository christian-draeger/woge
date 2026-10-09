# ADR 0048: Announce outcomes from the document, never from transport

- Status: Accepted
- Date: 2026-10-09
- Decision owners: Woge maintainers
- Related issues: [#120](https://github.com/christian-draeger/woge/issues/120), [#31](https://github.com/christian-draeger/woge/issues/31), [#35](https://github.com/christian-draeger/woge/issues/35), [#36](https://github.com/christian-draeger/woge/issues/36), [#38](https://github.com/christian-draeger/woge/issues/38)

## Context

Woge updates parts of a page without a full navigation: deferred regions now, enhanced forms and
live updates in M2. Sighted users see these changes. Screen-reader users only hear about them if the
page says so, usually through a live region (`role="status"`, `role="alert"` or `aria-live`).

Doing this badly is easy. If every patch is announced, users hear "loading", then the new content,
then "done" for one click. If nothing is announced, a form error stays unnoticed. If the framework
moves focus to make the reader speak, keyboard users lose their place.

A normal page load and a normal form post already behave well: the browser announces the new page
and the user starts at the top. That is the baseline Woge enhancement must not make worse.

[ADR 0026](0026-structured-deferred-region-execution.md) explicitly postponed automatic `aria-live`
and `aria-busy` until this policy exists.

## Decision

**1. The patch runtime stays silent.** Applying a patch never creates a live region, never writes
announcement text, never sets `aria-busy` and never moves focus. The Patch IR gets no `announce`
operation. Transport events (request started, chunk received, stream finished) are never announced.

**2. Announcements are document-owned.** The page decides what is announced by rendering a normal
live region and putting text into it through an ordinary patch. Put the live region in the page
shell, empty, so assistive technology knows it before the first update. Woge's job in M2 is to make
"the outcome of this action goes into that region" typed; it is a higher-level interaction outcome,
not a patch operation.

**3. Pick the announcement by use case.**

| Situation | What to use | Why |
| --- | --- | --- |
| Deferred region finishes during page load | Nothing. The loading text is replaced in place | Not caused by the user; the region is already announced when the user reaches it |
| Background live update (feed, counter) | Nothing by default; a `role="status"` summary only if the user asked to follow it | Frequent unrequested speech is noise |
| User action succeeded ("Saved") | `role="status"` (polite) | The user is waiting for a result, but nothing is wrong |
| Server-side validation failed | Error summary with links to the fields, focus moved to the summary (see 4) | Matches the accessible pattern for a full page reload with errors |
| User action failed, page still usable | `role="alert"` with a short sentence | The user must know their action did not happen |
| Recovery: page reloads | Nothing extra; the browser announces the new page | Same as native navigation |
| Recovery: stale or superseded update ignored | Nothing | The user never saw it, so there is nothing to correct |

`aria-live="assertive"` is reserved for `role="alert"`. Woge does not use it for success or progress.

**4. Focus and announcements have separate jobs.** Announcements tell users what happened. Focus
only moves when the user must act next, which in practice means the validation error summary after a
submit they started. Background updates, deferred regions and successful actions keep focus where it
is. Keeping focus and form state across replaced content is [#36](https://github.com/christian-draeger/woge/issues/36).

**5. Busy state belongs to the user's action.** While an enhanced form request runs, the submitting
form shows it: its submit button uses `aria-disabled="true"` (so it stays focusable and readable)
and the form or target region gets `aria-busy="true"` until the request ends or fails. Deferred page
regions do not get `aria-busy`; their loading text is the accessible state. If the JavaScript module
never runs, nothing stays hidden behind a busy flag.

**6. No repeated or stale speech.** Only the newest result of one interaction may be announced. A
patch that [ADR 0047](0047-canonical-failure-and-recovery-model.md) classifies as `ignore-stale`
never reaches a live region. One action writes at most one announcement.

## Alternatives considered

- **Add an `announce` operation to the Patch IR:** rejected for now. It would let any patch speak,
  duplicating content that is already in the patched HTML, and it would tie announcements to
  transport instead of to the user's action.
- **Announce every deferred region:** rejected; a page with five regions would speak five times
  without the user doing anything.
- **Server-rendered `aria-busy="true"` on deferred placeholders:** rejected; without the module the
  flag would never be cleared and some screen readers skip busy content.
- **Move focus to updated content:** rejected; it steals the user's place and breaks keyboard flow.

## Consequences

### Positive

- The runtime has no hidden accessibility side effects, so the page author always knows what is
  spoken. A browser test enforces it.
- Normal HTML live regions remain the tool, and they also work for the full-page fallback.
- M2 actions get one clear rule per outcome instead of ad-hoc choices.

### Negative

- Authors must render the live region themselves until M2 makes action outcomes typed.
- Some users may want announcements for live data; that is an explicit, opt-in page decision.

## Follow-up

- `client/woge-fallback-client/browser-tests/accessibility.spec.mjs` proves in Chromium, Firefox and
  WebKit that deferred loading, completion and superseded patches keep focus and create no live
  region or busy state.
- Validation ([#35](https://github.com/christian-draeger/woge/issues/35)), successful enhanced
  actions ([#31](https://github.com/christian-draeger/woge/issues/31)) and live updates
  ([#38](https://github.com/christian-draeger/woge/issues/38)) add their browser tests for rules 3–6
  when those features exist.
- Typed action outcomes in M2 decide which live region an action writes to; no Patch IR change is
  needed for that.

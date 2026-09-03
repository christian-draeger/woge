# AI-DX evaluation corpus v0.1

This corpus defines model-neutral tasks and observable results. It deliberately does not prescribe Kotlin API names: the evaluated release's public quick start and API must make the canonical names discoverable. Tasks are cumulative and begin from the same clean Spring Boot consumer scaffold.

## ADX-01 — First page

Build a page at `/projects/{project}` with a document title, skip link, navigation landmark, one `h1` and the selected project's name. Link to it from the application home page using an ordinary URL.

Expected outcome:

- the project name is HTML-escaped;
- valid and missing projects produce the documented status;
- the page and link work with JavaScript disabled;
- the application compiles and its page/browser checks pass.

## ADX-02 — Typed route and link

Add an optional `status` query input to the project page and create a link that selects open tasks. Rename the route input once and repair every affected call site using compiler feedback.

Expected outcome:

- links and route dispatch use the release's typed descriptors rather than duplicated path strings;
- invalid input follows the documented typed decoding behavior;
- the rename either updates safely or produces source-located actionable diagnostics.

## ADX-03 — Form action and validation

Add a form that creates a task with a required title, optional owner and optional ISO date. A blank title must show a linked error summary and field error while preserving submitted values.

Expected outcome:

- native `form`, `method`, `action`, names and controls remain visible in source/output;
- successful HTML-only submission uses the documented redirect behavior;
- native and enhanced paths share decoding, authorization, CSRF and validation;
- invalid data cannot be mistaken for a successful action.

## ADX-04 — Deferred region

Render the page shell immediately, then render the project summary and task table as independently completing regions. Make the test control completion order without sleeps.

Expected outcome:

- one portable application implementation works through every required server adapter;
- the enhancement path observes shell-first and completion-order output;
- the HTML-only path remains useful and never strands the user at a loading placeholder;
- cancellation stops outstanding child work.

## ADX-05 — Multi-target action

After creating a task, update the summary, task table and activity regions from one action.

Expected outcome:

- targets use typed rendered-region references, not hand-written CSS selectors;
- a stale or unknown target fails safely;
- focus and a concise completion announcement follow the documented contract;
- the server remains authoritative for all three results.

## ADX-06 — Authorized live update

Subscribe to project activity and append one event. Reconnect from the last event and reject a duplicate or stale update.

Expected outcome:

- authorization occurs before the live response commits and remains scoped to the project;
- event identity/revision behavior is deterministic;
- disconnect cancels the producer;
- ordinary navigation/refresh exposes the same data without JavaScript.

## ADX-07 — Compiler-guided repair

Start from the versioned negative fixture that intentionally supplies a page reference where an action target requires a rendered-region reference. Compile it, use only the diagnostic and public documentation to repair it, then rerun all checks.

Expected outcome:

- the invalid combination does not compile;
- the diagnostic identifies the received and required concepts at the application source location;
- a minimal valid example is discoverable without an internal repository search;
- the participant repairs the fixture without casts, suppression or raw-string escape hatches.

## ADX-08 — Standards-native styling

Style the resulting responsive screen first with plain modern CSS, then replace the theme with the documented optional Tailwind path without changing page/action/component semantics.

Expected outcome:

- external CSS, classes, custom properties, data/ARIA attributes and the cascade remain ordinary web concepts;
- the plain-CSS solution can use documented current features and fallbacks without a Kotlin property catalog;
- Tailwind utilities remain string classes and generated assets remain compatible with streamed patches;
- both variants pass the same accessibility and browser checks.

## Versioning

Changing expected behavior creates a new corpus version. Editorial clarification may update v0.1 when it does not alter success criteria. Every result names the exact corpus file commit. Versioned code fixtures land with the published scaffold and may refine concrete identifiers without weakening these outcomes.

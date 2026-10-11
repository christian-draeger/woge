# ADR 0102: Prove themes on the shared operations application

- Status: Accepted
- Date: 2026-10-11
- Decision owners: Woge maintainers
- Related issues: [#85](https://github.com/christian-draeger/woge/issues/85), [#93](https://github.com/christian-draeger/woge/issues/93)
- Builds on: [ADR 0016](0016-standards-native-css-authoring.md), [ADR 0098](0098-source-owned-component-registry.md), [ADR 0101](0101-native-data-display-and-complex-input-components.md)

## Context

The [M0 complex-screen contract](../architecture/frontend-extensibility.md) requires a coherent
application, not a collection of isolated controls. The reference hosts already share streamed project
regions, a versioned task action, live invalidation, recovery, native overlays and the bounded notes
island. Styling must not change those contracts or add bytes to the core browser client.

## Decision

Expand the shared task board into **Northstar operations**. It has navigation, delivery summary cards,
URL-backed title filtering, sorting and eight-row pages, owner/priority/status fields, create/edit
transactions, native task-detail dialogs with full-page edit fallbacks, and quiet live change notices.
Desktop tables retain row/column headers. Narrow screens expose task cards with the same actions;
only one representation is visible to assistive technology at a time. Task titles are unique in this
small in-memory reference; stale versions, duplicate names, unknown edits and invalid delivery fields
are rejected. This is a public demo, not an authenticated production issue tracker.

The same Kotlin application and component source serves plain CSS and Tailwind on WebFlux, MVC and
Ktor. `theme=tailwind` adds a stylesheet and a theme marker only. Ordinary component CSS remains
loaded in both themes. CSS uses layers, nesting, custom/logical properties, container queries and
color-scheme/contrast/motion preferences. Forced colors retain native system colors. Static utility
classes remain normal strings and never identify regions.

### Source ownership and scoping

Install `disclosure-card/css` through `dev.woge.components`, retaining its pristine lock snapshots,
license and provenance in the shared application. Customize the installed Kotlin with an operations
handbook label and `board-faq` class. Its stable `data-woge-scope="woge-disclosure-card"` marker and
zero-specificity authored selectors follow ADR 0098's accepted optional model. An application-owned
`overrides` layer sets its radius/border tokens and summary accent globally. This is explicit selector
ownership, not a newly invented `@scope` compiler or a fork of Woge core.

### Tailwind build and budgets

`buildReferenceTheme` uses the existing fallback-client package lock's Tailwind CLI. It scans the
shared Kotlin sources and compiles the checked-in CSS input into **ignored build output**, not
committed generated CSS. The reference browser tasks depend on this build, so the existing CI job's
npm installation verifies production CSS. Plain-CSS `processResources` does not depend on this task
or invoke Node. A plain artifact omits the optional stylesheet unless it was explicitly built first;
the walkthrough describes packaging the branded variant. The notes island's existing Node build is
independent of the plain theme's zero-Node CSS path.

The asset gate rebuilds Tailwind from the same source and measures it independently. New fixed
raw/gzip/Brotli ceilings are 3300/1200/1000 bytes for the operations CSS, 600/400/350 for the scoped
recipe, and 6000/2000/1800 for Tailwind. Existing ceilings, including the 36,000-byte core ceiling,
are unchanged. The ordinary reference CSS keeps its published minified measurement convention;
Tailwind is measured as shipped. No new JavaScript is introduced. Existing constrained-browser
shell, first-patch, patch-apply and action timing checks run against the expanded application.

## Alternatives considered

- Component-gallery-only evidence: rejected because it cannot demonstrate an application journey.
- Separate Tailwind renderer/model: rejected because themes must preserve action and patch semantics.
- Committed generated CSS: rejected because a clean browser job should verify the actual compiler output.
- Mandatory Node in JVM resources: rejected; the plain theme uses ordinary external CSS.
- New scoping compiler or runtime behavior: unnecessary; the accepted source-owned recipe model and
  existing primitives already supply stable selectors and native overlay behavior.

## Consequences

A single host-neutral source now exercises styling alongside real transactions and live updates.
The optional compiler is reproducible with tools already used in the reference CI job. The plain
path stays first-class, and new styles have explicit separate budgets rather than increasing core
ceilings. Two responsive representations increase server HTML, and title-based in-memory identity
is deliberately demo-only. Automated coverage does not replace editor, AT or documentation trials.

## Evidence

Shared Kotlin tests check semantic/region parity, source-owned scope markers, bounded URL operations,
atomic edits and invalid/stale commands. The reference-only Playwright suite covers both themes at
320/768/1440 pixels, dark mode, higher contrast, reduced motion, forced colors where emulation is
supported, keyboard dialog/focus flows, live notices and ordinary no-JavaScript create/filter/edit.
These fixtures never enter the standalone fallback-client config, which requires no Gradle server.
The existing application suite also covers loading/partial failures, native validation, reconnect,
patch ordering, silent background activity and the notes island. Five new tests per engine keep the
host job growth modest. See the [walkthrough](../guides/themed-reference-screen.md) for reproducible commands.

## Follow-up

- **Human developer and AI-assisted trial:** run the [trial protocol](../guides/themed-reference-trial.md)
  using only published docs; record outcomes rather than claiming these trials have happened. Live
  model evaluation belongs to #93 and must not become pull-request CI.
- **IntelliJ manual verification:** check inspections/completion of canonical `stylesheet(...)` and
  `declarations(...)` literals from the CSS guide, alongside ordinary external CSS. Record IDE/plugin
  versions and screenshots; automation here does not establish editor support.
- **Assistive-technology audit:** run the published manual audit with screen readers, keyboard-only
  navigation and real high-contrast settings. WebKit cannot emulate OS forced colors; check it manually.
- **Initial 1.0 compatibility/catalog findings:** native links/forms, headless dialogs and source-owned
  scoped recipes compose across both styling paths without new runtime bytes. Shared presentation
  preserves target identity. A small application stylesheet is still needed beside Tailwind; catalog
  recipes should expose low-specificity tokens, not promise framework-free application theming.
  Responsive dual representations trade extra server bytes for readable narrow-screen semantics;
  review this and title-based demo identity before recommending a production table recipe. Keep the
  current hybrid headless/source-owned catalog direction provisional until the trials and AT audit
  supply evidence. Record timings, failures and upgrade friction here before the 1.0 catalog decision.

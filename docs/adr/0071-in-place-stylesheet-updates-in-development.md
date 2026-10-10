# ADR 0071: Update stylesheets in place during development

- Status: Accepted
- Date: 2026-10-11
- Decision owners: Woge maintainers
- Related issues: [#152](https://github.com/christian-draeger/woge/issues/152), [#79](https://github.com/christian-draeger/woge/issues/79), [#205](https://github.com/christian-draeger/woge/issues/205)
- Builds on: [ADR 0041](0041-orchestrator-owned-spring-reload-and-sse-channel.md), [ADR 0044](0044-development-browser-snapshots-and-explicit-opt-in.md), [ADR 0068](0068-content-addressed-production-asset-trees.md), [ADR 0070](0070-optional-direct-vite-frontend-adapter.md)

## Context

Under `wogeDev`, saving a `.css` file used to refresh the whole document. A refresh loses focus,
open `<details>` and dialogs, video position and any form value that is not opted in to the
refresh handoff. CSS edits are the most frequent edits while styling a page, so this loop should be
as quick and quiet as in other web tooling, without adding Node.js, a proxy or a second channel.

## Decision

**A build in which only `.css` files changed is applied as `HOT_ASSET`.** Gradle still runs
`classes`, so `processResources` copies the new file into the running application's classpath. The
Spring child is not restarted: ADR 0041 only restarts it through the trigger file, and a
stylesheet-only build does not write that file. Any other change in the same build, or any
unknown file, keeps the normal restart and document refresh.

**The browser swaps the page's own `<link rel="stylesheet">` elements.** For each same-origin
stylesheet the client inserts a clone whose URL has `?woge-development-build=<build>`. When the
clone loads, the old link is removed, so the page is never unstyled. The query string bypasses
browser caches. Firefox still reuses an `@import`ed file it loaded before, so the client also points
each same-origin `@import` in the new sheet at a build-specific URL. Stylesheets from other origins,
such as a Vite dev server or the Woge overlay, are left to their owner. A newer update cancels
clones that have not loaded yet.

**A failed load falls back to one document refresh.** If any clone fails, for example because the
file was removed, the tab refreshes exactly as before.

**Snapshots carry `documentBuild` next to `renderedBuild`.** `renderedBuild` is the newest build
any tab may show. `documentBuild` is the newest build whose HTML changed (a server restart or a
document refresh). A tab refreshes when `documentBuild` or the generation is newer than its page;
when only `renderedBuild` is newer, it swaps stylesheets. Because SSE snapshots can be coalesced, a
tab that missed a Kotlin build still sees the newer `documentBuild` and refreshes. The client
rejects snapshots where `documentBuild` is greater than `renderedBuild`.

Production is unchanged: the client, the query parameter and the swap logic exist only in the
development modules, and production asset URLs stay content-addressed (ADR 0068).

## Alternatives considered

- **Keep a full document refresh for CSS:** rejected. It throws away page state for the most
  frequent kind of edit.
- **Replace `CSSStyleSheet` contents through CSSOM or constructable stylesheets:** rejected. It
  needs the CSS text in the channel, breaks relative URLs and `@import`, and changes how the page
  declares its styles.
- **Change the `href` of the existing link:** rejected. The browser drops the old rules before the
  new file arrives, which flashes unstyled content.
- **Send changed file names and update only those links:** deferred. Pages usually link few
  stylesheets; reloading all same-origin links is simpler and handles `@import` and renamed files.

## Consequences

### Positive

- Saving CSS keeps focus, scroll, form values and open UI in every tab.
- Measured in the browser fixture (20 saves, including a simulated 100 ms build): Chromium P50
  125 ms and P95 130 ms, WebKit P50 133 ms and P95 142 ms from save to applied style.
- The same path works for MVC and WebFlux, without Node.js, and is checked by the scaffold smoke.

### Negative

- A CSS change still waits for a Gradle `classes` run; the browser part adds only tens of
  milliseconds.
- All same-origin stylesheets reload, not only the changed one.
- Kotlin-generated CSS (inline `style` or DSL stylesheets) is HTML, so it still needs a restart.

## Follow-up

- Let the Gradle Tailwind adapter report its output as a CSS change so Tailwind rebuilds use the
  same path ([#79](https://github.com/christian-draeger/woge/issues/79)).
- Leave Vite-owned stylesheets to Vite HMR ([#205](https://github.com/christian-draeger/woge/issues/205)).

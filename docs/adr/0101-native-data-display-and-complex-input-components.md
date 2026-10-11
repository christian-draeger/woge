# ADR 0101: Compose native data display and complex inputs

- Status: Accepted
- Date: 2026-10-12
- Decision owners: Woge maintainers
- Related issues: [#84](https://github.com/christian-draeger/woge/issues/84)
- Builds on: [ADR 0098](0098-source-owned-component-registry.md),
  [ADR 0099](0099-native-form-components-and-validation.md),
  [ADR 0100](0100-native-navigation-overlay-and-feedback-components.md),
  [ADR 0073](0073-native-first-headless-ui-primitives.md)

## Context

Data-heavy applications need cards, tables, sorting, filtering, paging and inputs such as comboboxes,
dates and multi-selects. The server must stay the authority for data and order, URLs must stay
bookmarkable, and the core browser bundle (35,968 of 36,000 bytes) has no room for new behavior.

## Decision

Publish two registry items with `css` and reference `tailwind` variants, and no new JavaScript:

- `data-display-components`: card, badge, avatar, empty state, card grid, `dataTable`, `filterForm`,
  `PageWindow`, and `dataRegion` / `dataPanel` for patch-safe state.
- `complex-input-components` (depends on `form-components`): `formCombobox`, `formDateInput` and
  `formCheckboxGroup`.

### Native-control evaluation

| Need | Native control | Result |
| --- | --- | --- |
| Combobox / autocomplete | `input` + `datalist` | **Used.** Suggestions are hints; the server validates the text. Capped at 50 options. Bigger sets use a search results page. No custom listbox or ARIA combobox. |
| Date | `input type="date"` | **Used.** Always submits `yyyy-MM-dd`; the browser localizes the display. A `pattern` and placeholder cover browsers without a picker. |
| Multi-select | checkbox group, `select multiple` | **Checkbox group is the default** (touch and keyboard friendly, repeated names). `formSelect(multiple = true)` stays for compact, rarely changed lists. |
| Table on small screens | `table` in a scrollable `role="region"` | **Used.** Keeps table semantics; stacked `display: block` layouts were rejected because they drop them. |

None of these needed a custom behavior asset. A combobox enhancement is added only if a later audit shows
the native control is insufficient, and then as an opt-in asset loaded by that component alone.

### URL-driven sort, filter and paging

Sort headers and page numbers are links. Filtering is a `method="get"` form. All of them are built from
a typed `@WogeRoute` input, so they work without JavaScript and can be bookmarked. Changing sort or
filter drops the page. `PageWindow` clamps page and size (default 25, at most 100); the server loads
only one window, never the whole collection. Selection is a native checkbox per row inside an ordinary
action form.

### State semantics

`dataPanel` states are data, chosen by the server and replaced together with the rows:

| State | Meaning | Required markup |
| --- | --- | --- |
| `ready` | Current data | no notice |
| `loading` | New data coming | `aria-busy="true"`, visible notice |
| `stale` | Possibly outdated | visible notice and refresh link |
| `partial-error` | Some data missing | visible notice and retry link |
| `optimistic` | Change not yet confirmed | visible notice, `aria-busy` on pending rows |

Panels are never live regions. Use a stable feedback announcement region when users must hear a change.

### Patch concurrency

`dataRegion` is a stable root (`tabindex="-1"`, `data-woge-region`); patches replace its content only.
Rows, sort links and checkboxes carry stable keys (`data-woge-state-key`), so a sort or filter patch keeps
focus and checked rows and never duplicates them. A patch that would drop a checked row is rejected as a
whole. Stale replays are ignored by the existing revision check. Focus falls back to the region root.

## Alternatives considered

- Client-side sorting and filtering: ships all data and bypasses server authority.
- A JavaScript combobox or date picker: duplicates native, localized controls and costs bytes.
- A mobile card layout that replaces table markup: loses table semantics.

## Consequences

Contract tests compare both variants, check semantics, state rules, localization/RTL and windowing.
Generated fixtures from the real Kotlin recipes run in Chromium, Firefox and WebKit: keyboard use,
mobile width, patch concurrency, no-JavaScript GET sort/filter/paging, and axe checks.

## Follow-up

- Run manual screen-reader and touch audits for `datalist`, sort announcements and scroll regions.
- Consider an opt-in combobox enhancement only if audits show native `datalist` is not enough.
- Consider cursor (keyset) paging helpers for very large or fast-changing collections.
- Keep registry hashes, variant contracts and browser fixtures aligned when extending these bundles.

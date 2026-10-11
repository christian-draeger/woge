# Data display and complex inputs

Show lists of records, sort and filter them, and collect richer input — using plain HTML. Sorting and
paging are **links**, filtering is a **GET form**, so everything works without JavaScript and every
result has a URL you can bookmark.

Install the recipes with the [component registry](component-registry.md):
`data-display-components` and `complex-input-components` (which needs `form-components`).

## Cards, badges, avatars and empty states

```kotlin
cardGrid("Team", people.map { person ->
    DataItem(person.id) {                       // the id keeps the card stable across patches
        cardComponent(person.name) {
            avatarComponent(person.name)        // initials when there is no image
            badgeComponent(person.role)         // always text, never colour alone
        }
    }
})
if (people.isEmpty()) emptyState("No people", "Add a person to get started.")
```

## A typed URL for the page

Put the whole state of the page in a typed route (see [typed routes](typed-routes.md)):

```kotlin
@WogeRoute("/people")
data class PeopleInput(val q: String? = null, val sort: PeopleSort? = null, val dir: PeopleOrder? = null, val page: Int? = null)
```

## Sortable table

```kotlin
dataTable(
    UiId("people-table"), "People",
    columns = listOf(
        DataColumn("name", "Name", PeopleRoute.url(input.copy(sort = NAME, page = null)), sorted = ASCENDING),
        DataColumn("role", "Role", PeopleRoute.url(input.copy(sort = ROLE, page = null))),
    ),
    rows = people.map { DataRow(it.id, it.name, listOf({ text(it.name) }, { text(it.role) })) },
    selection = RowSelection("ids", "Select") { name -> "Select $name" },
)
```

- The sort header is a normal link. Only the sorted column gets `aria-sort`; the **server** sorts.
- The first cell is a row header. The table scrolls sideways inside a focusable region on small
  screens, and keeps real table semantics.
- Row checkboxes are native. Put the table inside a `formLayout(action)` to submit the selection.
- Row `key`s must be unique and use letters, digits, `-` or `_`.

## Filter and paging

```kotlin
filterForm("Filter people", PeopleRoute.url(PeopleInput()), "Filter",
    preserved = mapOf("sort" to "name", "dir" to "asc")) {
    label(attributes = { attribute("for", "q") }) { text("Name") }
    formInput(nameControl)
}

val window = PageWindow.of(input.page, size = 25, total = matchingCount)
// load only `window.size` rows starting at `window.offset`
pagination("Pages", window.pages().map { NavigationLink("$it", PeopleRoute.url(input.copy(page = it)), it == window.page) })
```

Never send the whole collection to the browser. `PageWindow` clamps bad page numbers and sizes (at most
100 per page). A new filter starts at page 1.

## Loading, stale, partial-error and optimistic states

Render the region once, then patch only its content:

```kotlin
dataRegion(UiId("people"), "People") { dataPanel(DataState.READY) { /* table */ } }

dataPanel(DataState.STALE, "These results may be out of date.", DataLink("Refresh", url)) { /* table */ }
```

| State | What the user sees |
| --- | --- |
| `READY` | Data, no notice |
| `LOADING` | Notice, `aria-busy`; keep showing the old rows |
| `STALE` | Notice and a refresh link |
| `PARTIAL_ERROR` | What loaded, notice and a retry link |
| `OPTIMISTIC` | Notice; pending rows (`DataRow(pending = true)`) have `aria-busy` |

The notice is visible text, not a live region. To announce a change, patch a stable announcement
region from `feedback-components`.

## Patches stay safe

Sort and filter patches keep focus and checked rows because rows, sort links and checkboxes have stable
keys. If a patch would remove a row the user checked, the browser rejects the patch instead of losing the
choice. An old patch (stale revision) is ignored.

## Complex inputs: native first

| Need | Use |
| --- | --- |
| Autocomplete | `formCombobox` — `input` + `datalist`. Hints only: **validate on the server**. At most 50 suggestions; use a results page for big lists. |
| Date | `formDateInput` — `input type="date"`. Sends `yyyy-MM-dd`; the browser shows the local format. |
| Several choices | `formCheckboxGroup` — checkboxes with one name. For a compact list use `formSelect(control, choices, multiple = true)`. |

```kotlin
formCombobox(cityControl, listOf(ComboboxSuggestion("Köln"), ComboboxSuggestion("Bonn", "Bonn (NRW)")))
formDateInput(dayControl, min = LocalDate.of(2026, 1, 1), max = LocalDate.of(2026, 12, 31))
formCheckboxGroup(tagsControl, "Topics", choices)
```

Checkbox groups submit repeated parameters (`tags=html&tags=css`); a `List<String>` field decodes them.
No JavaScript or custom ARIA widget is added. See [ADR 0101](../adr/0101-native-data-display-and-complex-input-components.md)
for the evaluation.

## Styling

Plain CSS ships with each recipe; the `tailwind` variant has the same markup with utility classes. Both
are source you own and can change.

# Navigation, overlays and feedback

These recipes give you useful HTML, not an SPA component framework. Install the bundles you need
with the [component registry](component-registry.md):

```bash
./gradlew wogeComponentAdd --name=navigation-components --variant=css --package=com.example.ui
./gradlew wogeComponentAdd --name=overlay-components --variant=css --package=com.example.ui
./gradlew wogeComponentAdd --name=feedback-components --variant=css --package=com.example.ui
```

Add `dev.woge:woge-ui-headless` to your application dependencies. Link each installed CSS file from
`/assets/components/` in the initial page head. The plain CSS variant needs no Node. For the optional
Tailwind presentation, choose `--variant=tailwind` and include installed Kotlin files in your normal
[Tailwind source scan](tailwind.md). Both variants have identical function signatures, HTML semantics
and `data-woge-scope` / `data-part` hooks.

## Navigation is links

Import the installed functions from your package. Inside a Woge HTML writer:

```kotlin
val destinations = listOf(
    NavigationLink("Overview", applicationUrl("/projects/42"), current = true),
    NavigationLink("Activity", applicationUrl("/projects/42/activity")),
)

breadcrumb("Breadcrumb", destinations)
navigationMenu("Project navigation", destinations)
navigationTabs("Project views", destinations)
navigationDropdown("More views", destinations)
pagination("Result pages", listOf(
    NavigationLink("Page 1", applicationUrl("?page=1"), current = true),
    NavigationLink("Page 2", applicationUrl("?page=2")),
))
```

`applicationUrl` comes from `dev.woge.html`; it checks the URL before writing `href`. Each navigation
landmark needs a descriptive, translated name. Set `current` on at most one link. Breadcrumbs use an
ordered list. Omit unavailable previous/next links; do not fake a disabled link.

URL tabs are links to complete pages, with `aria-current="page"`. They are **not** an ARIA tablist
that promises arrow-key switching between hidden panels. Likewise, the dropdown uses native
`details`/`summary` with ordinary links, not an application-menu role. Native navigation, reload,
Back, modifier clicks and optional [Woge navigation](enhanced-navigation.md) still work. No component
owns history or adds event listeners to links.

## Dialogs and drawers

```kotlin
val help = UiId("project-help")
overlayLink(help, applicationUrl("/projects/42/help"), "Project help")
dialogComponent(help, "Project help", "Close") {
    p { text("Your project is saved on the server.") }
}

val settings = UiId("project-settings")
overlayLink(settings, applicationUrl("/projects/42/settings"), "Settings")
drawerComponent(settings, "Settings", "Close settings") {
    p { text("Use ordinary native forms here.") }
}
```

`UiId` comes from `dev.woge.ui`. IDs must be unique across the page. Each title uses `<id>-title`;
reserve that ID too. Dialog triggers also have a stable Woge focus key. Render one trigger per
associated overlay, or give additional triggers their own keys in your source-owned recipe.

Both overlays use native `<dialog>`. A drawer is just edge styling, not a new interaction model.
Provide a real fallback page with the same useful content. Without JavaScript, the links open that
page. To open them modally, include a small application-owned module **only on pages with dialogs**:

```javascript
import { installWogeDialogs } from "/assets/woge-ui/dialog.js";
const disposeDialogs = installWogeDialogs();
```

Load this file with an ordinary external `<script type="module" src="…">` in the initial document.
Do not include it in every page or import it into the core client. The returned function removes its
listener when your application needs cleanup. The listener also handles triggers added by patches.
There is no hydration and no new JavaScript package to install.

The browser keeps modal focus inside, makes background content inert and supports Escape. A native
`form method="dialog"` close button receives initial focus and returns focus to the trigger. Outside
clicks deliberately **do not** close a modal dialog. Never wrap the entire dialog content in a form
when it already contains the close form; compose separate native forms instead. Opening and closing
a modal does not change the URL.

## Popovers and tooltip help

```kotlin
popoverComponent(UiId("project-options"), "Options") {
    p { text("Extra settings go here.") }
}
tooltipComponent(UiId("visibility-help"), "About visibility", "Only project members can see this.")
```

These use native `popovertarget` and `popover="auto"`; no optional module is loaded. Popovers close
on Escape or outside interaction. Tooltip help is explicitly opened by click, touch or keyboard,
with `aria-describedby` connecting its description. It is not hover-only: do not put essential field
instructions only in a tooltip. The tooltip body is text, with no interactive controls. Use a popover
for interactive content and a dialog when the rest of the page must wait. In browsers without
popover support, the explanatory content remains inline rather than depending on a script fallback.

## Feedback without repeated announcements

```kotlin
alertComponent("The report could not be saved.")
progressComponent("Uploading attachment", value = 25.0, maximum = 100.0)
progressComponent("Loading report") // no value means indeterminate
skeletonComponent()
toastComponent(UiId("report-saved")) { text("Report saved.") }

// Once per document, outside lists and replaceable component containers:
announcementRegion(UiId("report-status"))
announcementRegion(UiId("report-alert"), LiveRegion.ALERT)
```

`LiveRegion` comes from `dev.woge.ui`. Static alerts and toast bodies have **no live role**. A toast
stays until your application removes it; it has no timer and does not steal focus. Add ordinary
links/forms for actions or server-owned dismissal. Avoid putting the same message in multiple live
regions. Use `status` for ordinary updates and `alert` only for urgent failures.

The two announcement roots start empty and remain in the accessibility tree. Their unique IDs are
also registered Woge region names, initially at revision zero. Target those names in your normal
[typed action patches](typed-actions.md), replacing only their text. Do not call `announcementRegion`
inside a patch targeting the existing region: that would nest another root instead of updating it.
For complete native navigation, also render useful visible feedback; initial live-region content is
not a reliable announcement mechanism.

Progress uses native `<progress>` with a translated accessible name. Values must be finite and between
zero and the positive maximum. Skeletons are decorative and hidden from screen readers. Include
visible loading text and `aria-busy="true"` on the owning content region; clear both when work finishes.
Reduced-motion settings stop skeleton animation. The recipes do not infer pending state or work duration.

## Safe server patches

Keep overlays inside the region that owns them; do not move them into a body portal. The existing
patch runtime closes overlays it removes before detaching their nodes. Their replacement starts
closed, and the optional dialog helper returns focus to a replacement trigger when available.
Patching only the content of a stable dialog region can intentionally retain that open dialog.

Keep announcement roots **outside** replaced containers, and patch their content separately. Registered
region uniqueness prevents duplicate roots; revisions suppress stale replayed messages. Do not append
new live regions for every toast or render HTML containing another copy of a registered announcement
region. No component can guarantee exactly-once spoken output across all assistive technologies;
verify timing with your supported screen readers.

Plain CSS uses logical properties for RTL and tokens such as `--woge-ink`, `--woge-surface`,
`--woge-line` and `--woge-focus`. Tailwind exposes the same hooks and semantic contracts. Both variants
are compile-tested, compared for semantic parity, and have a shared three-engine browser suite using
fixtures generated from the real Kotlin recipes. Automated accessibility checks supplement, not
replace, manual screen-reader and touch audits.

Run the focused verification with `./gradlew :woge-component-registry:check`. This runs the browser
contracts in Chromium. After generating fixtures and compiling the reference Tailwind stylesheet,
you can run the same suite in other installed engines from `client/woge-fallback-client`:

```bash
npm exec -- playwright test navigation-overlay-feedback.spec.mjs --project=firefox --project=webkit --workers=2
```

Browser installation and operating-system launch failures are separate from component contract
failures; complete the additional engine and manual assistive-technology audits before release.

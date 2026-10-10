# Accessible UI primitives

`woge-ui-headless` gives you four small building blocks for common interface patterns. Each one
writes a native HTML element with the right attributes. The browser handles keyboard, focus and
screen readers. You add the looks with your own CSS or Tailwind classes.

```kotlin
dependencies {
    implementation("dev.woge:woge-ui-headless:<version>")
}
```

| You want | Use | HTML you get | Needs JavaScript |
| --- | --- | --- | --- |
| Show/hide a section | `disclosure` | `<details><summary>` | No |
| A modal that blocks the page | `dialogLink` + `modalDialog` | `<a href>` + `<dialog>` | Optional, 663 B gzip |
| A small panel on top of the page | `popoverButton` + `popoverPanel` | `<button popovertarget>` + `<div popover>` | No |
| Tell screen readers about a change | `liveRegion()` | `role="status"` or `role="alert"` | No |

The reference [task board](../../examples/reference-application/shared/src/main/kotlin/dev/woge/example/project/TaskBoardMarkup.kt)
uses all four with plain CSS. The [Tailwind example](../../examples/tailwind-spring-boot/src/main/kotlin/example/tailwind/StatusPage.kt)
uses the same four with Tailwind classes. Why it works this way: [ADR 0073](../adr/0073-native-first-headless-ui-primitives.md).

## Disclosure

```kotlin
disclosure(summary = { text("What happens when I add a task?") }, attributes = { classes("faq") }) {
    p { text("Only the task list changes.") }
}
```

Pass `open = true` to render it open. Clicking or pressing Enter/Space on the summary toggles it.

## Modal dialog

A dialog starts as a normal link to a page that shows the same content. That page is what people
without JavaScript get, and what opens in a new tab on Ctrl/Cmd-click.

```kotlin
val help = UiId("board-help")

dialogLink(help, TaskBoardRoute.url(TaskBoardInput(TaskBoardView.HELP))) { text("Board help") }

modalDialog(help, title = "Board help", attributes = { classes("dialog") }) {
    p { text("Type a title and press Enter.") }
    dialogCloseButton { text("Close") }
}
```

Load the optional module once to open the link as a modal instead:

```js
import { installWogeDialogs } from "/assets/woge-ui/dialog.js";

installWogeDialogs();
```

The file is served from the `woge-ui-headless` jar. In the modal, focus moves inside, the rest of
the page is inert, and Escape or the close button closes it. Focus then returns to the link, even
if a patch replaced the link in the meantime. The title is the dialog's accessible name.

## Popover

```kotlin
val legend = UiId("legend")

popoverButton(legend) { text("Legend") }
popoverPanel(legend, attributes = { classes("popover") }) {
    p { text("ok works, warning is slow, down does not work.") }
}
```

Escape or a click outside closes it, and focus returns to the button. Use a popover for small
extras. Use a modal dialog when the user must finish or dismiss something first.

## Live region

Render the element empty, then put text into it with a patch ([ADR 0048](../adr/0048-document-owned-accessibility-announcements.md)).

```kotlin
region(BoardStatusRegion.target(page), "", elementName = "p", attributes = {
    attribute("id", "board-status")
    liveRegion()                 // role="status": read when the user is idle
})
p(attributes = { liveRegion(LiveRegion.ALERT) }) {}   // role="alert": read immediately
```

## Styling recipes

The markup never changes between styling approaches; only the classes do.

Plain CSS, from the reference application:

```css
.faq > summary { cursor: pointer; font-weight: 600; }

.dialog,
.popover {
  border: 1px solid var(--line);
  border-radius: 0.85rem;
  background: var(--panel);
  padding: 1rem 1.25rem;
}

.dialog::backdrop { background: rgb(0 0 0 / 0.45); }
```

Tailwind, from the Tailwind example:

```kotlin
disclosure(
    summary = { text("How often is this page updated?") },
    attributes = { classes("rounded-lg border border-line p-3") },
    summaryAttributes = { classes("cursor-pointer font-semibold") },
) { /* … */ }

modalDialog(incident, title = "Uploads incident", attributes = {
    classes("m-auto max-w-md rounded-lg border border-line bg-surface p-6 backdrop:bg-black/50")
}) { /* … */ }
```

Every primitive also sets `data-woge-ui="disclosure" | "dialog" | "popover"`, so you can style all of
them at once with `[data-woge-ui="dialog"]` if you prefer.

## Content-Security-Policy

No primitive writes inline scripts, inline styles or event attributes. They work with
`script-src 'self'; style-src 'self'`.

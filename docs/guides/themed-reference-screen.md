# Build and extend the themed operations screen

Northstar is the reference application's task board, not another UI framework. Start the primary host:

```sh
./gradlew :woge-reference-spring-webflux:bootRun
```

Open `/projects/woge/tasks`. Plain CSS is the default; its build needs no Node/CSS processor.
The reference application's existing Kotlin/JS notes widget has a separate Node build; plain CSS
itself does not require that toolchain. MVC and Ktor run the same Kotlin screen:

```sh
./gradlew :woge-reference-spring-mvc:bootRun
./gradlew :woge-reference-ktor:run
```

Use one host at a time, or set `SERVER_PORT` (Spring) / `PORT` (Ktor).

## Build the optional brand

The reference browser job already installs the fallback client's locked dependencies. From a clean
checkout, explicitly install those tools and build the optional theme before starting or packaging a host:

```sh
(cd client/woge-fallback-client && npm ci --ignore-scripts --no-audit --no-fund)
./gradlew buildReferenceTheme
./gradlew :woge-reference-spring-webflux:bootRun
```

Open `/projects/woge/tasks?theme=tailwind` or use **Northstar Tailwind**. The generated stylesheet lives
under `shared/build/generated/theme-assets`, which all three hosts package through their shared
resources. It is not checked in. Plain CSS can be built without invoking `buildReferenceTheme`;
that artifact does not contain the branded stylesheet. Build the theme first for a two-theme deployment.
Restart/repackage after building it; a running host does not hot-reload classpath resources.

`shared/src/main/tailwind/operations.css` imports Tailwind's theme/utilities, deliberately not its
preflight reset. It scans the same shared Kotlin files for complete utility tokens. The ordinary
`application.css`, `operations.css` and scoped component CSS coexist with it. There are no dynamic
utility names, two copies of server business logic, hydration or additional core client code.

## Walk the screen

1. Use **Navigate** to visit the streamed project overview or the notes workspace. It is a native
   disclosure menu on narrow screens, so it works without JavaScript.
2. Read delivery summary cards. Filter titles with the GET form; the URL can be copied or bookmarked.
   Sort A–Z/Z–A and follow task-page links. The default order is newest first; pages contain eight tasks.
3. Enter a title and owner; choose priority/status. Submit **Add task**. Native required/length rules
   provide immediate validation. The server also validates the values, unique title and board version.
4. Open **Details** from a table row or narrow-screen card. With JavaScript it is a native modal and
   Escape returns focus. Without JavaScript the same link opens task details and the edit form.
5. Follow **Edit task**, change delivery fields/title and submit **Save task**. The count, rows, version
   controls and status update in one transaction. Filter/order/page context is preserved by the form.
   Theme switches intentionally return to a create view; they never replay an edit.
6. Add or edit a task in another tab. The first tab gets a quiet **task changed** notice, not an automatic
   list replacement or screen-reader announcement. Follow **Show the latest board** to return to the complete, default plain-CSS board.
   Without JavaScript, reload to see changes. A stale form is refused rather than overwriting newer data.

The board is shared, in-memory demo data. Restarting clears it. Names are unique and are the demo's
edit keys, not authorization credentials. Production apps should use durable record IDs and their
own authorization. Data errors and stale-write failures use the existing host failure contract;
the enhanced alert advises reloading rather than pretending a write succeeded.

## Find the code

All paths below are relative to `examples/reference-application/shared`:

| File | Owns |
| --- | --- |
| `src/main/kotlin/dev/woge/example/project/TaskBoard.kt` | Shared page, versioned action and live use cases |
| `BoardPresentation.kt` in that package | Theme selection and bounded filtering/order/page logic |
| `BoardNavigation.kt`, `BoardTable.kt`, `TaskBoardForm.kt` | Native links, tables/cards, overlays and grouped fields |
| `src/main/resources/static/assets/operations.css` | Application presentation and preference modes |
| `src/main/tailwind/operations.css` | Optional branded build input |
| `src/main/kotlin/dev/woge/example/components/DisclosureCard.kt` | Installed, customized source-owned recipe |
| `woge-components.lock.json`, `woge-components/licenses/` | Installer provenance and pristine comparison sources |

Use the [component registry guide](component-registry.md) to install/review updates. This app installed
`disclosure-card/css` in package `dev.woge.example.components` through `wogeComponentAdd`; it then
added the handbook label and board class **in the installed file**, leaving the lock's base unchanged.
The explicitly authored scope remains stable on every render. The application `overrides` CSS layer
changes radius/border custom properties and adds a summary accent without modifying core or recipe CSS.
No CSS rewriting compiler is implied. [CSS and assets](css-and-assets.md) explains Kotlin CSS literals;
[form components](form-components.md) and [data display](data-display-and-complex-inputs.md) show richer recipes.

## Check changes

```sh
./gradlew :woge-reference-shared:test performanceBudgets
./gradlew referenceBrowserSmokeWebFlux
./gradlew referenceBrowserSmoke # all three adapters
```

The browser tasks build the optional stylesheet first and use the separate reference Playwright
config. If port 8080 is occupied, set `WOGE_REFERENCE_PORT=18585` on the browser command. The existing
constrained Chromium test reports shell/stream/patch/action timings. The asset gate reports core,
ordinary CSS, scoped recipe and branded CSS separately; never raise an existing ceiling to pass.
No live model evaluation runs in these tests. Human editor, screen-reader and extension-trial work
is tracked in [ADR 0102](../adr/0102-themed-reference-operations.md#follow-up).

# Frontend extensibility and complex-screen acceptance

This document is the shared acceptance contract for styling, component-distribution and optional client-behavior work. It describes observable web behavior, not a proposed Kotlin API.

## Extension lanes

Woge supports four additive lanes. An application can stop after any lane.

| Lane | Authoring surface | Ownership |
| --- | --- | --- |
| Semantic server HTML | Native elements, attributes, typed Woge pages/actions/regions | Server-authoritative and useful without JavaScript |
| Standards-native styling | External CSS, stylesheet/declaration literals, classes, custom properties and cascade | Application; Woge transports strings and assets without a CSS allowlist |
| Small browser behavior | External JavaScript modules, custom elements or headless controllers using standard DOM APIs | Application/component; lifecycle is explicit around patch application |
| Local island | An explicit subtree with its own client renderer/state when browser-local interaction requires it | Island adapter inside a documented boundary; surrounding page remains server-owned |

Tailwind, CSS Modules-style names and future style processors use the styling lane. They may generate assets or class names but cannot become the semantic component/action API. A local island is an escape hatch, not the default rendering model.

## DOM and styling ownership

- Region identity, patch targeting, revisions and focus keys use Woge metadata, never styling classes or arbitrary CSS selectors.
- Every element accepts ordinary ordered classes, `style`, custom properties, `id`, `data-*`, `aria-*`, custom attributes and custom-element names. Woge does not interpret utility tokens or rewrite application class order.
- A replace operation owns the server-rendered region subtree. Focus, dirty controls and explicitly preserved application state follow one patch-preservation contract; a third-party controller cannot assume arbitrary nodes survive replacement.
- A local island owns only its declared root. Woge does not patch through that root unless the island's adapter has an explicit update/dispose contract. Island state is not silently treated as authoritative server state.
- Headless behavior mounts, updates and disposes through a small lifecycle tied to real DOM nodes. It uses event delegation or custom-element lifecycle where suitable and requires no virtual DOM or application-wide hydration graph.
- Component state and variants may be typed in Kotlin and rendered as semantic attributes/classes. CSS values and Tailwind utilities remain ordinary strings rather than generated Kotlin property/utility enums.
- Page assets are normal stylesheet links and external module scripts with explicit URL, CSP nonce/integrity and loading semantics. Core flows require no inline executable handler.
- Plain CSS and the HTML-only workflow are the reference implementation. Removing Tailwind, an optional controller or an island cannot change page/action/patch wire semantics.

## Representative complex screen

The project operations page is expanded into one coherent screen rather than a component gallery:

- responsive application header, primary navigation and collapsible narrow-screen navigation;
- project summary cards and live activity feedback;
- URL-backed task filtering, sorting and pagination;
- a dense create/edit form with grouped controls, validation summary and field messages;
- a task data table that becomes a readable narrow-screen representation without losing header relationships;
- a detail dialog or drawer enhancement with an ordinary page fallback;
- loading, empty, partial-error, validation-error, stale-update and reconnect states;
- light, dark, forced-colors/high-contrast and reduced-motion behavior;
- one application-owned custom element or headless controller and one bounded local-island proof when that lane is implemented.

All variants reuse the same page, action, component and region model. A styling or behavior option may add assets and attributes, but it cannot fork server business logic.

## Measurable acceptance matrix

| Area | Required check |
| --- | --- |
| Responsive layout | The journey is usable at 320 CSS px, a mid-size viewport and a wide desktop viewport with no hidden action or two-dimensional page overflow; intentional table overflow is labeled and keyboard reachable |
| Keyboard/focus | The complete create/filter/update/dialog journey works without a pointer; patch replacement, removal, validation and navigation have deterministic focus outcomes |
| Semantics | Landmarks, heading order, native labels, table relationships and status messages pass automated checks plus the published manual audit |
| Preferences/themes | Light, dark, forced-colors and reduced-motion modes retain readable focus, contrast and state; information never depends on color or motion alone |
| No JavaScript | Navigation, filtering, pagination, create/edit/status mutation and the dialog's task complete as ordinary page/form journeys; live data remains available by refresh |
| Patch stability | Plain-CSS, Tailwind and custom-element variants produce the same target/revision outcomes; styling classes never affect identity; dirty value and focus fixtures pass |
| CSS openness | Current selectors, at-rules, values and custom properties from the browser policy pass through unchanged; unknown valid syntax is not rejected by Woge |
| Tool interchange | The same semantic component is rendered with plain CSS and Tailwind; removing either theme changes only classes/assets and presentation tests |
| Optional behavior | Each controller/island records its external module bytes, lifecycle tests, CSP behavior, no-JavaScript fallback and supported-browser subset |
| Performance | Reports separate compressed core runtime, each opt-in behavior, plain-CSS output, Tailwind output, first shell/patch timing and patch-apply time under the reference fixture |
| AI/human DX | Corpus ADX-08 can find the normal class/CSS/module escape hatches and switch styling paths without invented Woge APIs or semantic changes |

Numeric release limits belong to [#46](https://github.com/christian-draeger/woge/issues/46), after the first implementations provide evidence. Until then, a solution fails this contract if it silently adds an application-wide client framework, requires hydration for a core workflow, hides an unmeasured runtime/style asset, or cannot produce separate measurements.

## Evidence required from dependent spikes

- [#88](https://github.com/christian-draeger/woge/issues/88): external CSS, IDE-recognized literals, modern syntax preservation and optional deterministic scoping.
- [#77](https://github.com/christian-draeger/woge/issues/77): Kotlin/generated-source extraction, static class rules, plain-CSS coexistence and reproducible Tailwind output.
- [#76](https://github.com/christian-draeger/woge/issues/76): one substantially customized complex component in each viable source-owned, binary/headless or hybrid distribution model.
- [#4](https://github.com/christian-draeger/woge/issues/4): cross-browser patch behavior with styling/custom-element preservation and no executable patch content.
- [#80](https://github.com/christian-draeger/woge/issues/80): shared accessibility and lifecycle behavior for headless primitives.

Evidence is comparable only when it uses this screen, state matrix and the [browser-support policy](browser-support-policy.md). Isolated visual demos can inform a decision but cannot satisfy the contract.

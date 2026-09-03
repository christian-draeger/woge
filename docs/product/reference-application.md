# Project operations reference application

This document defines the product fixture used to evaluate Woge. It describes observable web behavior, not a proposed framework API.

## User journey

A user opens `/projects/{project}` and receives an immediate page shell containing:

- a skip link, site navigation and one page heading;
- project identity and ordinary navigation URLs;
- semantic loading placeholders for three deferred regions;
- a native link to the task-creation path.

The server completes these regions independently:

| Region | Content | Behavior to prove |
| --- | --- | --- |
| Project summary | Open, completed and overdue task counts | Fast deferred replacement |
| Task table | Tasks, status, owner and due date | Slower data-rich replacement and later filtering |
| Recent activity | Ordered project events | Deferred append and later SSE reuse |

The deliberately slower task table may arrive after a region declared later. Tests control completion order instead of relying on sleeps.

## Mutations and validation

The first complete mutation flow supports:

1. creating a task with a title, optional owner and optional due date;
2. rejecting a blank title with a field error, summary and predictable focus;
3. changing a task between open and completed;
4. updating both project summary and task table after a successful mutation;
5. appending an activity item and issuing a polite announcement.

Native forms use normal names, methods and action URLs. Without JavaScript, successful mutations use Post/Redirect/Get and validation renders a useful HTML response. Enhanced requests may receive patches, but server validation and authorization remain authoritative.

## Live update

An authorized SSE subscription emits a new project activity event. Reconnection uses an event ID, and a duplicate or stale event cannot apply the same visible update twice. Without JavaScript, the activity page remains available through ordinary navigation and refresh.

## Host matrix

The application and component source is shared across:

- Spring Boot with Spring MVC;
- Spring Boot with Spring WebFlux;
- Ktor.

Only host bootstrap and framework integration may differ. Adapter tests assert equivalent status, headers, HTML, patch semantics, errors and cancellation where the host exposes it.

## UI growth path

The same page later expands to prove complex frontend work:

- responsive application navigation;
- task filtering, sorting and pagination in the URL;
- dense but accessible form layouts;
- dialog or drawer enhancement with a normal page fallback;
- loading, empty, validation, partial-error and stale states;
- light, dark, high-contrast and reduced-motion behavior;
- replaceable plain-CSS and Tailwind themes.

CSS classes never identify patch targets. UI behavior must not require Kotlin/JS, Kotlin/Wasm, hydration or a virtual DOM.

## Measurements

Record results against the hand-written Spring baseline before setting release budgets.

### Developer experience

- time from a clean consumer scaffold to the first rendered page;
- time to add the first deferred region and typed action;
- number of application-owned string URLs, action IDs and patch selectors;
- number of files and lines needed for host-specific glue;
- compile-error repair success for a web developer new to Kotlin and for the AI-DX corpus.

### Web performance

- time to first response byte and first shell byte;
- time of each deferred patch relative to request start;
- enhanced action round-trip and patch-apply time;
- compressed base runtime and generated CSS size;
- server render allocations and throughput under a documented fixture.

### Reliability and compatibility

- adapter TCK pass rate for all three hosts;
- stable-browser and no-JavaScript journey pass rate;
- stale or duplicate patch rejection rate in deterministic race fixtures;
- disconnect cancellation and SSE reconnect behavior;
- reproducibility of generated source and production assets.

### Accessibility and security

- keyboard-complete journey and predictable focus restoration;
- automated violations plus a documented manual screen-reader review;
- equivalent CSRF, validation and authorization behavior in native and enhanced requests;
- strict-CSP and patch-injection fixture pass rate.

Exact thresholds belong to the performance-budget decision after baseline data exists. A metric without a recorded environment, fixture and comparison is not accepted as evidence.

## Milestone slices

| Milestone | Reference result |
| --- | --- |
| M0 | Domain, baseline and measurement method are fixed |
| M1 | Immediate shell and three deferred regions run on every host |
| M2 | Typed forms, validation, multi-region updates and SSE complete the CRUD journey |
| M3 | Security, accessibility, cancellation, browser and production behavior are hardened |
| M4 | Navigation, themes and the accessible component catalog produce a polished complex application |

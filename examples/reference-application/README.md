# Reference application

This directory is the maintained consumer of Woge's public modules. Its project-operations domain and
journeys are defined in [ADR 0004](../../docs/adr/0004-project-operations-reference-application.md).
It is deliberately a normal web application: a GET returns useful semantic HTML, CSS is served as an
ordinary asset, and a small module optionally fetches independently completed region patches.
The task board also demonstrates one real form action updating several typed regions.

## Start the primary path

From a fresh checkout with JDK 21, start the primary Spring Boot WebFlux application:

```shell
./gradlew :woge-reference-spring-webflux:bootRun
```

Then open `http://localhost:8080/projects/woge`.

## Run another host

All launchers expose the same paths and browser behavior:

```shell
./gradlew :woge-reference-spring-webflux:bootRun
./gradlew :woge-reference-spring-mvc:bootRun
./gradlew :woge-reference-ktor:run
```

Run one command at a time; each uses port 8080. WebFlux is the primary documented non-blocking Spring
path, MVC proves Servlet compatibility, and Ktor proves that the Woge application boundary does not
depend on Spring.

## Follow the web flow

1. `GET /projects/woge` returns the heading, navigation, native complete-page link and three useful
   loading regions immediately.
2. `/assets/application.js` reads the page's declared patch URL and fetches
   `GET /projects/woge/woge-patches/{epoch}`. Each rendered page has a fresh random epoch; the typed
   URL returns it to the server so deferred patches target that document, not a later navigation.
3. Each completed region arrives as a versioned replace patch and becomes visible without waiting for
   slower siblings.
4. With JavaScript disabled, the **Load all project data as one complete page** link performs a normal
   navigation to `/projects/woge?view=complete`; no patch runtime is required.

## Try a multi-region action

Follow **Task board** to `/projects/woge/tasks`. Enter a title and submit:

- Without JavaScript, the POST returns a 303 to the board's canonical GET. Refresh does not add another task.
- With enhancement, one response updates the count, list, hidden ordering fields and one success status.
  The visible input and submit button stay outside replaced regions, so focus and typed text remain intact.

The generated `AddBoardTaskAction` supplies the ordinary form URL; `BoardSummaryRegion`,
`BoardTasksRegion`, `BoardStateRegion` and `BoardStatusRegion` supply typed targets. There are no
application-selected CSS patch selectors. Hidden controls use HTML's `form="board-form"` attribute,
so the same command is submitted natively and through browser `FormData`.

This is a public, in-memory demo board, not a production authentication example. The built-in action
context checks same-origin requests before reading the action body. A mismatched Origin or missing
same-origin evidence is rejected with 403. For TLS-terminating proxies, configure trusted forwarded
headers so the observed origin matches the public URL. Deployed applications should also configure
their normal authentication and, when needed, token-based CSRF policy; see the
[typed action guide](../../docs/guides/typed-actions.md).

The server checks the title and board version on every submission. A stale version returns 409
without mutation; reload the canonical GET before trying again. Epochs and revisions are ordering
data, not authorization. Each new document has a fresh epoch. All four replacements are rendered
before the in-memory state is committed, in declaration order. A rendering error returns no
successful subset; a lost response never causes automatic POST replay.

For explicit region recovery, the generated `BoardRegionRoute` exposes the read-only
`GET /projects/woge/tasks/regions/{epoch}/{target}/{revision}/{interaction}?query=...`.
Only the task-list target is supported. With patch-stream `Accept`, it returns one typed Replace
of current matching tasks; ordinary navigation redirects to the full board. The base revision
describes the browser's current DOM, not the board's domain version. `runtime.beginInteraction`
supplies the requesting context and `runtime.refetchRegion` bounds recovery. Neither replays a POST.
The browser fixtures deliberately apply an older search response after a newer one and hold an
action response while submitting again, so ordering and duplicate suppression are deterministic
on all three hosts.

When another visitor adds a task, open boards show a live notice: "1 new task was added. Show the
latest board". The page opens `GET /projects/woge/tasks/live/{epoch}` with `EventSource`. That stream
names only the activity region; the browser then loads the notice with the safe
`GET /projects/woge/tasks/activity/{epoch}/{target}/{revision}/{interaction}?since=<version>`.
The notice sits in `role="status"`, never moves focus and links to the normal board instead of
changing the list under the user's cursor. Without JavaScript or the stream, the board works as before.

[`shared`](shared) contains the host-neutral `ProjectPage`, semantic HTML, region work and web assets.
[`spring-webflux`](spring-webflux), [`spring-mvc`](spring-mvc) and [`ktor`](ktor) contain only their
host-specific startup, routes and real-server integration tests. The example consumes root projects
and is verified by `./gradlew check`; it is executable documentation, not a published Woge artifact.
Its runtime classpath includes the public `woge-fallback-client-assets` integration instead of copying
browser-runtime source into the example build.

The same Playwright source verifies enhanced and no-JavaScript journeys on all three hosts in
Chromium, Firefox and WebKit:

```shell
./gradlew referenceBrowserSmoke
```

The mutation checks use one browser worker because they intentionally share the same board.
For a local browser run while another server owns port 8080, set `WOGE_REFERENCE_PORT`, for example:

```shell
cd client/woge-fallback-client
WOGE_REFERENCE_HOST=spring-mvc WOGE_REFERENCE_PORT=18082 npm run test:reference
```

The [Spring Boot quickstart](../../docs/guides/quickstart-spring-boot.md) introduces the required
Kotlin syntax just in time. The [Ktor guide](../../docs/guides/ktor-adapter.md) explains the secondary
bootstrap. Reproducible source measurements and comparison with the hand-written M0 Spring fixture
live in the [M1 baseline](../../docs/performance/m1-multi-host-baseline.md).

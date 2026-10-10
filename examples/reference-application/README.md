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

This is a public, in-memory demo board, not a production authentication example. Each host requires
exactly one matching `Origin` header before reading the action body. Missing, `null`, duplicate or
foreign origins are rejected with 403. No permissive CORS or forwarded-header trust is installed.
For a deployed application, configure trusted proxy/origin handling and the host's normal
authentication and CSRF policy; see the [typed action guide](../../docs/guides/typed-actions.md).

The server checks the title and board version on every submission. A stale version returns 409
without mutation; reload the canonical GET before trying again. Epochs and revisions are ordering
data, not authorization. Each new document has a fresh epoch. All four replacements are rendered
before the in-memory state is committed, in declaration order. A rendering error returns no
successful subset; a lost response never causes automatic POST replay.

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

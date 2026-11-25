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

## Try the packaged reference

From the repository root with JDK 21 and Python 3, select `spring-webflux`, `spring-mvc` or `ktor`:

```shell
python3 scripts/reference-demo.py run spring-webflux
python3 scripts/reference-demo.py run ktor --port 18083
```

Each command builds and starts one host on loopback. Spring uses its executable Boot JAR. Ktor
uses its installed application distribution. Open the URL printed by the runner and stop it with
Ctrl+C. The project and board start from the same fixture data in each new process. Edits are
stored in memory for that process. The packaged runner starts the ordinary reference profile.

Use `run` or a local image to show the application, `verify` to check behavior, and `measure` to
collect isolated browser timing samples. Keep walkthroughs and timing runs in separate processes.
Public shared-host timings describe that visit only. A public mutable deployment has separate
provisioning, work and storage limits, reset and isolation requirements. This fixture's notes and
task board retain additions until the process stops. Run the local reference without production
secrets or data.

To verify the shared browser journeys against the packaged artifacts, first install the pinned
browser tooling in `client/woge-fallback-client` with `npm ci` and
`npx playwright install chromium firefox webkit`. Then run from the repository root:

```shell
python3 scripts/reference-demo.py verify spring-webflux
python3 scripts/reference-demo.py verify spring-mvc
python3 scripts/reference-demo.py verify ktor
```

Verification uses a fresh loopback port unless supplied and Playwright shuts down the server.
The runner stores build and browser logs, JSON and HTML browser results, failure traces and a
versioned evidence manifest under `build/reference-demo`. The manifest identifies the source
checkout, dirty state, host, selected browser projects, toolchains and packaged file hashes.
Use `--project chromium-enhanced --project chromium-no-javascript` for a focused run and retain
the selected projects in the manifest. The default runs all three engines in both modes.

To collect the existing timing-budget samples, run `python3 scripts/reference-demo.py measure ktor`
or select a Spring host. The runner verifies correctness first, then starts a fresh packaged process
and runs the throttled Chromium probe in a separate report directory. The raw samples and conditions
are in `measurement/browser-budget.json`. These samples describe the selected fixture and checkout.
Use the [comparison contract](../../docs/performance/comparison-contract.md) for independent
performance experiments and comparisons. The separate packaged
[Todo release](../../docs/guides/todo-container.md) has its own version and deployment rules.
The [25 November 2026 three-host study](../../docs/performance/reference-project-deferred-2026-11-25.md)
records the pinned source, versions, resource limits, raw outcomes and experimental scope for a
separate run of the shared deferred-project workload.

### Build a local reference image

With Docker running, build exactly one of the same packaged hosts. The runner uses a pinned
Java 21 image and records the source revision, Woge version, packaged file hashes, platform
and image ID under `build/reference-demo`.

```shell
python3 scripts/reference-demo.py image spring-webflux
python3 scripts/reference-demo.py image spring-mvc
python3 scripts/reference-demo.py image ktor
```

Substitute the image tag printed by the selected build.

```shell
docker run --rm --cpus 2 --memory 512m --read-only --tmpfs /tmp \
  -p 127.0.0.1:18083:8080 woge-reference-ktor:<printed-revision>
```

Open `http://127.0.0.1:18083/projects/woge`. Use the same limits and pin the image ID for a
controlled study. These images are local source builds. The in-memory fixture resets when
the container exits. The port mapping keeps the demonstration on loopback.

## Walk through the shared fixture

Start a [packaged host](#try-the-packaged-reference) and use its printed origin for the paths below.

1. Open `/projects/woge` and watch the regions fill independently. Open it again with JavaScript
   disabled and follow **Load all project data as one complete page**.
2. Turn JavaScript back on. On `/notes`, enter one space in **New note** and choose **Add note**.
   Check the validation message, replace the space with a short note and submit it. Reload and check
   that it appears once.
3. Follow **Task board** from the project and add a task. Enter its title in **Filter tasks** and
   choose **Apply filter**. With JavaScript disabled, repeat with a different title and refresh the
   board after saving to check that the native flow adds it once.
4. With JavaScript enabled, open the unfiltered board in two tabs. Leave an unsent **Task title**
   draft in the first tab and add a different task in the second. Return to the first tab and check
   that its draft remains. Use **Show the latest board** to see the new task.

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

The demo board stores state in memory and accepts same-origin actions without a sign-in. The built-in
action context checks same-origin requests before reading the action body. A mismatched Origin or missing
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

When another visitor adds a task, open boards show the live notice "1 task changed. Show the latest
board". The page opens `GET /projects/woge/tasks/live/{epoch}` with `EventSource`. That stream
names only the activity region; the browser then loads the notice with the safe
`GET /projects/woge/tasks/activity/{epoch}/{target}/{revision}/{interaction}?since=<version>`.
The notice is ordinary, non-live HTML: it stays silent, never moves focus and links to the normal
board instead of changing the list under the user's cursor. A polite live summary is appropriate only
after the user explicitly chooses to follow updates. Without JavaScript or the stream, the board works as before.

## Maintain the shared fixture

[`shared`](shared) owns the seeded data, page behavior, markup, and web assets. [`shared-rules`](shared-rules)
owns the shared note validation. [`spring-webflux`](spring-webflux), [`spring-mvc`](spring-mvc) and
[`ktor`](ktor) own their startup, request and transport bindings, and real-server integration tests.
The [shared browser suite](../../client/woge-fallback-client/reference-browser-tests) checks the
same journeys on those packages. [The runner](../../scripts/reference-demo.py) owns packaged
launches and their evidence manifests.

After changing fixture data, behavior or assets, run `./gradlew check` and rerun `verify` on all
three packaged hosts. Include the [adapter contract checks](../../testing/woge-adapter-tck) when
changing a host boundary. Add a Quarkus slot after its full adapter contract passes. [Quarkus contract](https://github.com/christian-draeger/woge/issues/298)

Record the exact source, packages and browser assets when collecting new evidence. Give changed
workloads a new study identity and preserve the old manifests and raw results. The frozen Todo
comparison retains its own workload and artifacts. [Frozen Todo comparison](../../spikes/todo-framework-comparison/isolated-results.md)

This executable reference consumes root projects and uses the public `woge-fallback-client-assets`
integration for its browser runtime.

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

The [accessibility guide](../../docs/guides/accessibility.md) maps the shared automated contract,
keyboard-only reference journeys, dashboard stress checks and the manual audit checklist.

For the complete operations journey and optional Tailwind brand, see the [themed screen walkthrough](../../docs/guides/themed-reference-screen.md).

## Production deployment verification

`./gradlew productionDeploymentTest` packages MVC, WebFlux and Ktor and runs real Docker/nginx smoke
and rolling-update checks. It is opt-in and requires a running Docker engine, Java 21 and Python 3,
not Node or a browser. See [production operations](../../docs/guides/production-operations.md) for
private health endpoints, bounded stream shutdown, retained assets and deployment settings.
The `/deployment` confirmation is stateless; the notes/task-board stores remain in-memory demos,
not a replicated production database.

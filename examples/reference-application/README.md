# Reference application

This directory is the maintained consumer of Woge's public modules. Its project-operations domain and
journeys are defined in [ADR 0004](../../docs/adr/0004-project-operations-reference-application.md).
It is deliberately a normal web application: a GET returns useful semantic HTML, CSS is served as an
ordinary asset, and a small module optionally fetches independently completed region patches.

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
   `GET /projects/woge/woge-patches`.
3. Each completed region arrives as a versioned replace patch and becomes visible without waiting for
   slower siblings.
4. With JavaScript disabled, the **Load all project data as one complete page** link performs a normal
   navigation to `/projects/woge?view=complete`; no patch runtime is required.

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

The [Spring Boot quickstart](../../docs/guides/quickstart-spring-boot.md) introduces the required
Kotlin syntax just in time. The [Ktor guide](../../docs/guides/ktor-adapter.md) explains the secondary
bootstrap. Reproducible source measurements and comparison with the hand-written M0 Spring fixture
live in the [M1 baseline](../../docs/performance/m1-multi-host-baseline.md).

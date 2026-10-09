# Woge Spring Boot application

This is the small canonical Woge application scaffold. It renders normal HTML on the server, loads an
ordinary CSS file and remains complete when JavaScript is unavailable. You can use familiar browser
tools and web standards; Woge supplies Kotlin type safety and a narrow server-adapter boundary.
The generated `AGENTS.md` gives coding tools version-matched rules and links to the same canonical
documentation used here.

## Run the first page

The default host is Spring Boot WebFlux:

```shell
./gradlew bootRun
```

Open `http://localhost:8080/`. The first run needs JDK 21. It does not need Node.js, Vite, Tailwind or
a JavaScript application build.

The scaffold pins Woge, Kotlin, Spring Boot, selected host, build-JDK and JVM-target versions in
`gradle.properties`.
Woge is pre-release; repository CI supplies `-PwogeRepository=/path/to/local/repository` until the
coordinates are publicly available.

## Develop with live reload

```shell
./gradlew wogeDev
```

`wogeDev` builds the application, starts it and rebuilds after every save. Open tabs refresh once the
new version is ready. Compile errors appear in the terminal and in a small overlay with file, line and
column, while the last working version keeps serving. It needs no Node.js.

- `--port=9090` uses another HTTP port.
- `--full-restart` always starts a new JVM instead of the faster Spring DevTools restart.
- `-PwogeSpringAdapter=mvc` works here too.

Restart `wogeDev` after changing Gradle files. `./gradlew check` also verifies that the `bootJar`
contains no development code.

If your app sends a `Content-Security-Policy` header, `wogeDev` allows its own client in development
only. Give your head scripts or stylesheets a nonce and the client reuses it. `./gradlew wogeTasks`
lists all supported tasks; add `--format=json` for tools and coding agents.

## Choose WebFlux or MVC

Start with the default before making an architecture decision. If your application needs the Servlet
ecosystem, select MVC explicitly:

```shell
./gradlew bootRun -PwogeSpringAdapter=mvc
```

`src/main` contains the page and application. The tiny host adapters live in `src/webflux` and
`src/mvc`; Gradle compiles exactly one. The page itself imports no Spring, Reactor or Servlet type.
When materializing from the Woge repository, pass `mvc` as the optional second argument to persist
that selection and generate matching coding guidance.

## Read the project as a web developer

- `HomePage.kt` writes familiar `html`, `head`, `body`, `h1` and `p` elements. Dynamic values go
  through `text(...)`; URLs use `applicationUrl(...)`.
- `styles.css` is browser CSS with cascade layers, custom properties, nesting, logical dimensions,
  `light-dark()`, `oklch()`, `color-mix()` and a container query. Woge does not translate it.
- `WebFluxRoutes.kt` or `MvcRoutes.kt` maps the normal `/` HTTP route to the same page port.
- `ApplicationTest.kt` starts a real random-port server and verifies HTML, CSS and selected adapter.
- `AGENTS.md` is generated from the versioned framework index, public documentation and persisted
  host choice. Change those canonical inputs instead of editing the generated file.

The generated-source root is `build/generated/sources/woge/main/kotlin`. Generators own only that
directory; edit application source under `src` and never commit generated output.

## Test

Run the normal Node-free test:

```shell
./gradlew test
```

The optional focused browser test uses Playwright only as test tooling:

```shell
npm ci
npx playwright install chromium
WOGE_REPOSITORY=/path/to/woge/repository npm run test:browser
```

The browser starts the same Gradle application with JavaScript disabled and verifies semantic HTML,
the external stylesheet and useful content. Playwright is not a production dependency.

Tailwind and Vite are intentionally absent. Add them later only when the application needs a frontend
asset pipeline. Ktor is a supported Woge host, but it is not installed in this Spring-first scaffold.

For more context, read the Woge
[Spring Boot quickstart](https://github.com/christian-draeger/woge/blob/main/docs/guides/quickstart-spring-boot.md)
and [Kotlin for web developers](https://github.com/christian-draeger/woge/blob/main/docs/guides/kotlin-for-web-developers.md).

## Provenance

The wrapper, pinned tool versions, verification defaults and small-project layout follow the selected
patterns recorded in Woge's
[scaffold provenance](https://github.com/christian-draeger/woge/blob/main/docs/development/scaffold-provenance.md),
derived from `christian-draeger/kotlin-library-template` commit
`33103bcaf6015f038266e41c2309e6f522ec00f8`. Library publishing and release automation were not copied
into this application scaffold.

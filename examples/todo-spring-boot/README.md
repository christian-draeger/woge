# Woge Todo

A small full-stack application: normal HTML forms, server validation, typed Kotlin actions, reactive
list/count updates and an in-memory repository. The same action serves native POST/Redirect/GET and
enhancement. Open two tabs to see live updates.

## Run from published packages

Install JDK 21. Copy this directory anywhere; it has its own Gradle wrapper and consumes Woge
`0.1.0-alpha.1` from GitHub Packages, independently of the Woge build.

GitHub Packages requires a username and a classic token with `read:packages`. Set
`ORG_GRADLE_PROJECT_githubPackagesUsername` and `ORG_GRADLE_PROJECT_githubPackagesPassword`
in your environment as described in the [package guide](https://github.com/christian-draeger/woge/blob/main/docs/guides/packages.md). Never commit
a token or place it on the command line.

```shell
./gradlew bootRun
```

Open [localhost:8080](http://localhost:8080). No Node, Vite or Tailwind is required. Use
`./gradlew wogeDev` for the edit/rebuild/restart loop or `./gradlew wogeDev --mcp` for the
experimental local agent tools.

Try adding a blank title, completing an item and filtering the list. Disable JavaScript and repeat:
forms redirect to the same canonical GET and validation preserves your text.

**Public demo:** all visitors share one process-local in-memory store. Restarting deletes all todos.
Run one instance. Durable or replicated deployments need an application-owned shared store and
the corresponding session/event distribution; Woge does not supply a database.

## Read the application

- [Todos.kt](src/main/kotlin/example/todo/Todos.kt): domain model, repository contract and synchronized memory store.
- [TodoActions.kt](src/main/kotlin/example/todo/TodoActions.kt): server validation and mutations.
- [TodoPage.kt](src/main/kotlin/example/todo/TodoPage.kt): semantic HTML, native forms and typed regions.
- [TodoRoutes.kt](src/main/kotlin/example/todo/TodoRoutes.kt): Spring-only HTTP bootstrap.
- [todo.css](src/main/resources/static/todo.css): ordinary CSS.

The shipped browser client enhances declared forms and live regions. Application code contains no
JavaScript. A live invalidation triggers a current HTML GET; it does not create a second domain-object
store in the browser. The add form is outside the live board, so another visitor's mutation preserves
your unsaved draft. Render identity/revisions are ordering metadata, never permissions.

## Verify

The [container guide](https://github.com/christian-draeger/woge/blob/main/docs/guides/todo-container.md)
shows the pinned non-root image, health probes and real nginx/browser verification.

```shell
./gradlew check bootJar
```

Tests cover concurrent repository writes, native redirects, validation/preserved values, denied
cross-origin writes and enhanced responses decoded by the real patch codec.

For real browsers, Node is test-only:

```shell
npm ci
npx playwright install chromium firefox webkit
npm run test:browser
```

Build `bootJar` first. The browser gate starts the production JAR and checks Chromium, Firefox and
WebKit with enhancement and JavaScript disabled. Failure traces/screenshots stay in `test-results`.

Maintainers can override `-PwogeRepository=/absolute/path/to/maven-repository` and
`-PwogeVersion=...` to test freshly built artifacts. The checked-in default always uses the
published release; no composite build or local repository is required by consumers.

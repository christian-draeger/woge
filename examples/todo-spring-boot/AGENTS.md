# Woge Todo application guidance

Humans and agents use this application's public Kotlin/HTML APIs and the same verification commands.

## Checked-in baseline

- wogeVersion=0.1.0-alpha.1
- kotlinVersion=2.4.10
- kspVersion=2.3.12
- springBootVersion=4.1.1
- Host: Spring Boot WebFlux; build JDK 21, JVM target 17.
- Node/Playwright are browser-test-only. No frontend build is needed to use the application.

The baseline describes gradle.properties. Maintainers may explicitly override the Maven repository
and version to test a candidate build; inspect the actual dependency version before using a new API.
The current example keeps its validation path compatible with its published alpha.

## Work and verify

1. Read README.md, Todos.kt, TodoActions.kt, TodoPage.kt and the small TodoRoutes.kt host boundary.
2. Use ./gradlew wogeDev while editing. ./gradlew wogeTasks --format=json lists the supported commands.
3. Run ./gradlew check bootJar for repository concurrency, native HTTP, validation and real patch-codec tests.
4. Run npm ci, npx playwright install chromium firefox webkit, then npm run test:browser for browser behavior.
5. Generated Kotlin belongs under build/generated/ksp. Change source declarations, never generated files.

./gradlew wogeManifest writes structural application metadata. It is not proof of route installation,
authorization or runtime state. Experimental ./gradlew wogeDev --mcp exposes build state/diagnostics
through the local session file build/woge-dev/mcp.json. Treat that file's token as a credential.
Use the client's existing browser tool for clicking, screenshots and visual checks.

## Application contracts

Keep the useful HTML-only path. The same typed action supplies native POST/Redirect/GET and enhanced
patches. Domain data is server-owned; filter state uses typed URLs; unsubmitted text stays in the
native add form. That form is outside the live board so other visitors do not replace the draft.
Repository mutations and snapshot reads use the same lock. Store data is shared and process-local.

Use text(value) for dynamic text and typed URL APIs for attributes. Use the generated route/action/
region descriptors. Query properties of a WogeRoute input are nullable; the page supplies its default.
Use addForm.field(AddTodo::title, ...) for serialized field metadata. If a property has SerialName,
supply its serialized name explicitly to the field API.

Do not replay an uncertain POST. Request IDs and revisions are not authorization or idempotency.
Preserve only selected display-safe text on validation, never security credentials. The public demo
uses the host's same-origin policy; a real account-specific application must supply its own
authentication/authorization and shared persistence/event infrastructure.

Kotlin types catch supported structural mistakes. Tests still verify business rules, server policy,
HTML semantics and real browser behavior. If documentation and compilation disagree, check the pinned
Woge version and report the mismatch instead of inventing an API, casting or bypassing validation.

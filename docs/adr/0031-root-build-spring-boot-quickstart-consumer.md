# ADR 0031: Keep the canonical Spring Boot quickstart as a separated root-build consumer

- Status: Accepted
- Date: 2026-09-05
- Decision owners: Woge maintainers
- Related issues: [#73](https://github.com/christian-draeger/woge/issues/73), [#24](https://github.com/christian-draeger/woge/issues/24), [#27](https://github.com/christian-draeger/woge/issues/27), [#74](https://github.com/christian-draeger/woge/issues/74)

## Context

The first guide must prove that the published module boundaries form a usable Spring Boot application,
not merely show fragments copied from unit tests. It should start with one standard Gradle command,
exercise immediate HTML plus deferred regions, remain understandable without JavaScript and create the
seam where Spring MVC and Ktor can be added without rewriting application code.

The first complete document also exposed two gaps in the low-level writer: standards mode required a
raw HTML escape hatch for the doctype, and familiar elements still required string tag names despite
ADR 0012 choosing common tag wrappers as the documented path.

## Decision

The maintained reference application joins the root Gradle build as two consumer projects:

- `woge-reference-shared` owns the project data, semantic HTML, `PageUseCase` and deferred regions and
  has no Spring, Reactor, Servlet or Ktor dependency;
- `woge-reference-spring-webflux` owns Spring Boot startup, functional routes, static assets and a
  real-server integration test.

The documented command is `./gradlew :woge-reference-spring-webflux:bootRun`. Root `check` compiles and
tests both projects. Documentation excerpts point to those canonical sources rather than creating an
uncompiled sample tree.

The enhanced URL returns a useful HTML shell with three region placeholders. A small ordinary ES
module fetches the versioned deferred stream and delegates decoding/application to the maintained
fallback client. The development build copies that client's unbundled canonical source into the
example resources without requiring Node.js. This is a repository-example convenience, not the final
browser-package distribution contract.

The same page exposes a normal GET form to `?view=complete`. That full-navigation response renders all
region content on the server and does not load the patch module. Unknown projects and view modes keep
ordinary 404 and 400 behavior.

`HtmlWriter.doctype()` writes only the standard `<!doctype html>` declaration. Common document,
sectioning, text, list, table and form tags receive thin typed wrapper functions over the existing
writer. Their names and nesting follow HTML; `element(...)` and `voidElement(...)` remain available for
new, uncommon and custom elements. The wrappers do not add a DOM, widget model or property catalog.

## Alternatives considered

- **Keep snippets only in documentation:** rejected because source can drift and does not prove the
  dependency graph or Boot startup.
- **Put portable page and Spring routes in one module:** rejected because framework leakage would be
  possible before MVC/Ktor parity work begins.
- **Make JavaScript mandatory for final content:** rejected because the native navigation must remain
  useful and testable.
- **Render all data before the initial response:** retained as the explicit full-navigation path but
  rejected as the only path because it cannot prove completion-order deferred rendering.
- **Commit a second bundled fallback-client copy:** rejected because generated browser code would have
  two sources of truth.
- **Require string names for every HTML tag:** retained as the forward-compatible escape hatch but
  rejected as the primary guide because common tags should be discoverable and typo-resistant.

## Consequences

### Positive

- The guide, application, adapter wiring and tests describe one executable system.
- The host-neutral boundary is enforced by Gradle projects rather than prose alone.
- Web developers can inspect familiar HTML, CSS, JavaScript, URLs, forms and status codes.
- Common tags gain Kotlin completion while custom and future platform elements remain open.
- MVC and Ktor can add launchers around the same shared page in #24.

### Negative

- The repository has two additional verification projects and a slower real-server test.
- The development-only source copy does not define npm/Maven/browser package publication.
- The initial wrapper set must later be generated or mechanically checked against standards data.
- The full-navigation query mode is an explicit example policy, not a universal Woge convention.

## Follow-up

- Add Spring MVC and Ktor launchers plus cross-host browser coverage in [#24](https://github.com/christian-draeger/woge/issues/24).
- Replace manual route/epoch reconstruction with generated descriptors in [#27](https://github.com/christian-draeger/woge/issues/27).
- Expand compile-verified examples and diagnostics in [#74](https://github.com/christian-draeger/woge/issues/74).
- Generate the complete standards-derived tag surface in [#131](https://github.com/christian-draeger/woge/issues/131).
- Publish a normal browser-client consumption path in [#132](https://github.com/christian-draeger/woge/issues/132).

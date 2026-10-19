# 🌊 Woge

**HTML-first reactive web development for Kotlin.**

Woge is an HTML-first Kotlin framework for typed, server-driven and progressively enhanced web applications.

Woge is pronounced `/ˈvoːɡə/` ("VOH-guh"). It is the German word for a wave or swell: the server
renders a working HTML page first, and small updates then roll in over it. The name is only a
picture; Woge does not invent wave-themed API names.

Woge is for teams who want to build web applications with HTML, HTTP, forms and CSS on the server.
Client-side UI toolkits such as React, Vue or Compose for Web are a good fit when the browser should
own most of the UI state. Woge takes the other path: the server owns the HTML, every page works
without JavaScript, and Kotlin types catch mistakes at compile time.

Woge is in alpha (`0.1.0-alpha.1`). Typed routes, forms/actions, validation, deferred/live regions and
ordinary CSS run on Spring Boot WebFlux, Spring MVC and Ktor. Automated adapter, browser and container
checks cover the shared contracts. Human usability trials and independent release reviews remain
open; Woge is not ready for production use yet.

Start with the [standalone Todo application](examples/todo-spring-boot/README.md) for one complete
form/validation/update journey using published dependencies, or follow the framework quickstart below.

**For humans and coding agents:** typed public declarations, compiler diagnostics and fast server-side
tests support the same readable HTML/HTTP workflow. Version-matched guidance and the experimental
development MCP make application structure and build state discoverable. See the
[human and agent workflow](docs/guides/human-and-agent-workflow.md) for commands, verification layers
and the limits of current evidence.

Run the maintained example with `./gradlew :woge-reference-spring-webflux:bootRun`, then open
`http://localhost:8080/notes`. The [web-first quickstart](docs/guides/quickstart-spring-boot.md)
explains one page and form, its POST/Redirect/GET fallback and its enhanced action.
The [mental model and DX doctrine](docs/guides/mental-model.md) introduce the next layers.
The same portable page runs on MVC with `./gradlew :woge-reference-spring-mvc:bootRun` and on Ktor
with `./gradlew :woge-reference-ktor:run`.
`./gradlew referenceBrowserSmoke` runs the same enhanced and no-JavaScript journeys through all three
hosts in Chromium, Firefox and WebKit.

For a small standalone starting point, see the
[versioned Spring Boot application scaffold](scaffolds/spring-boot/README.md). It consumes normal Woge
coordinates, defaults to WebFlux, can select MVC explicitly and starts with type-safe semantic HTML and
ordinary modern CSS without requiring Node.js.

To use Woge in your own Gradle build, see the
[minimal Gradle project](docs/guides/packages.md#minimal-gradle-project) (GitHub Packages, version
`0.1.0-alpha.1`).

## Direction

- HTML, CSS, links, forms, HTTP, URLs and browser APIs remain visible.
- Spring Boot is the primary host integration; Spring MVC, Spring WebFlux and Ktor use the same framework-neutral core.
- JavaScript enhances a working web application instead of becoming a prerequisite for core workflows.
- Kotlin types, generated descriptors and compiler diagnostics replace avoidable strings and runtime magic.
- Standard HTML tags are a generated Kotlin DSL with specification-linked completion and open platform fallbacks.
- Accessibility and security are part of normal component and action behavior.
- Plain CSS is always supported and Tailwind is an optional build adapter. Components combine stable binary headless primitives with application-owned source recipes.

## Project documentation

- [Documentation index](docs/README.md)
- [Production operations and rolling deployment](docs/guides/production-operations.md)
- [MVP boundary](docs/mvp-boundary.md)
- [Architecture decisions](docs/adr/README.md)
- [Documentation style guide](docs/documentation/style-guide.md)
- [AI-assisted developer-experience criteria](docs/ai-dx/evaluation.md)
- [Contributing](CONTRIBUTING.md)
- [Roadmap](https://github.com/users/christian-draeger/projects/1)

## License

Woge is licensed under the [Apache License 2.0](LICENSE).

## Follow-up

| Area | Current delivery and next step |
| --- | --- |
| Packages | [Authenticated GitHub Packages consumption](docs/guides/packages.md) and [explicit Maven publication](docs/development/publishing.md); Maven Central remains later work. |

# Woge

**HTML-first reactive web development for Kotlin.**

Woge is an HTML-first Kotlin framework for typed, server-driven and progressively enhanced web applications.

Woge is pronounced `/ˈvoːɡə/` ("VOH-guh"). It is the German word for a wave or swell: the server
renders a working HTML page first, and small updates then roll in over it. The name is only a
picture; Woge does not invent wave-themed API names.

Woge is for teams who want to build web applications with HTML, HTTP, forms and CSS on the server.
Client-side UI toolkits such as React, Vue or Compose for Web are a good fit when the browser should
own most of the UI state. Woge takes the other path: the server owns the HTML, every page works
without JavaScript, and Kotlin types catch mistakes at compile time.

The M0 architecture and product-validation baseline is complete. The first M1 multi-host vertical
slice is executable on Spring Boot WebFlux, Spring MVC and Ktor; Woge is not ready for production use
yet.

Run the maintained example with `./gradlew :woge-reference-spring-webflux:bootRun`, then open
`http://localhost:8080/projects/woge`. The [web-first quickstart](docs/guides/quickstart-spring-boot.md)
explains the page, its full-navigation fallback and the small amount of Kotlin it uses.
The same portable page runs on MVC with `./gradlew :woge-reference-spring-mvc:bootRun` and on Ktor
with `./gradlew :woge-reference-ktor:run`.
`./gradlew referenceBrowserSmoke` runs the same enhanced and no-JavaScript journeys through all three
hosts in Chromium, Firefox and WebKit.

For a small standalone starting point, see the
[versioned Spring Boot application scaffold](scaffolds/spring-boot/README.md). It consumes normal Woge
coordinates, defaults to WebFlux, can select MVC explicitly and starts with type-safe semantic HTML and
ordinary modern CSS without requiring Node.js.

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
- [MVP boundary](docs/mvp-boundary.md)
- [Architecture decisions](docs/adr/README.md)
- [Documentation style guide](docs/documentation/style-guide.md)
- [AI-assisted developer-experience criteria](docs/ai-dx/evaluation.md)
- [Contributing](CONTRIBUTING.md)
- [Roadmap](https://github.com/users/christian-draeger/projects/1)

## License

Woge is licensed under the [Apache License 2.0](LICENSE).

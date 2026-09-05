# Woge

Woge is an HTML-first Kotlin framework for typed, server-driven and progressively enhanced web applications.

The M0 architecture and product-validation baseline is complete. The first M1 Spring Boot WebFlux
and MVC vertical slices are executable from the repository; Woge is not ready for production use yet.

Run the maintained example with `./gradlew :woge-reference-spring-webflux:bootRun`, then open
`http://localhost:8080/projects/woge`. The [web-first quickstart](docs/guides/quickstart-spring-boot.md)
explains the page, its full-navigation fallback and the small amount of Kotlin it uses.
The same portable page runs on MVC with `./gradlew :woge-reference-spring-mvc:bootRun`.

## Direction

- HTML, CSS, links, forms, HTTP, URLs and browser APIs remain visible.
- Spring Boot is the primary host integration; Spring MVC, Spring WebFlux and Ktor use the same framework-neutral core.
- JavaScript enhances a working web application instead of becoming a prerequisite for core workflows.
- Kotlin types, generated descriptors and compiler diagnostics replace avoidable strings and runtime magic.
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

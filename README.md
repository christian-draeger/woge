# Woge

Woge is an HTML-first Kotlin framework for typed, server-driven and progressively enhanced web applications.

The project is in its architecture and product-validation phase. It is not ready for application use yet.

## Direction

- HTML, CSS, links, forms, HTTP, URLs and browser APIs remain visible.
- Spring Boot is the primary host integration; Spring MVC, Spring WebFlux and Ktor use the same framework-neutral core.
- JavaScript enhances a working web application instead of becoming a prerequisite for core workflows.
- Kotlin types, generated descriptors and compiler diagnostics replace avoidable strings and runtime magic.
- Accessibility and security are part of normal component and action behavior.
- Plain CSS is always supported. Tailwind and the component distribution model are still being evaluated.

## Project documentation

- [Documentation index](docs/README.md)
- [Architecture decisions](docs/adr/README.md)
- [Documentation style guide](docs/documentation/style-guide.md)
- [AI-assisted developer-experience criteria](docs/ai-dx/evaluation.md)
- [Contributing](CONTRIBUTING.md)
- [Roadmap](https://github.com/users/christian-draeger/projects/1)

## License

Woge is licensed under the [Apache License 2.0](LICENSE).

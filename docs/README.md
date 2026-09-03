# Woge documentation

The documentation grows with executable product slices. Pages describing unimplemented behavior must say so clearly.

## Start here

- [Project direction](../README.md)
- [Architecture decision records](adr/README.md)
- [Reference application](product/reference-application.md)
- [Hand-written Spring HTML baseline](../spikes/spring-html-htmx-baseline/evidence.md)
- [Spring MVC and WebFlux support model](architecture/spring-support-model.md)
- [Browser support and progressive enhancement](architecture/browser-support-policy.md)
- [Frontend extensibility and complex-screen acceptance](architecture/frontend-extensibility.md)
- [Component identity, page epochs and revisions](architecture/identity-and-revisions.md)
- [Typed web-reference spike](../spikes/typed-reference-model/evidence.md)
- [HTML writer strategy spike](../spikes/html-writer-strategy/evidence.md)
- [Patch framing spike](../spikes/patch-framing/evidence.md)
- [Cross-browser fallback patch runtime spike](../spikes/fallback-patch-runtime/evidence.md)
- [Native Declarative Partial Updates spike](../spikes/native-dpu/evidence.md)
- [Threat model](security/threat-model.md)
- [Documentation style guide](documentation/style-guide.md)
- [AI-assisted developer-experience criteria](ai-dx/evaluation.md)
- [AI-DX evaluation corpus](ai-dx/corpus-v0.1.md)

## Planned documentation layers

1. **Quick start:** build one useful page with Spring Boot.
2. **Mental model:** relate Woge pages, regions, actions and patches to HTML and HTTP.
3. **Task guides:** solve forms, validation, streaming, live updates, styling and deployment.
4. **API reference:** list exact types, defaults and compatibility constraints.
5. **Internals:** explain protocols, generated code and adapter implementation.

Only the architecture and quality foundations exist today. The quick start begins with the walking skeleton.

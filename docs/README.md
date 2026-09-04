# Woge documentation

The documentation grows with executable product slices. Pages describing unimplemented behavior must say so clearly.

## Start here

- [Project direction](../README.md)
- [MVP boundary and definition of done](mvp-boundary.md)
- [Reference application](product/reference-application.md)
- [Architecture decision records](adr/README.md)

## Canonical architecture guidance

- [M1 module and consumer graph](architecture/module-graph.md)
- [Spring MVC and WebFlux support model](architecture/spring-support-model.md)
- [Browser support and progressive enhancement](architecture/browser-support-policy.md)
- [CSS authoring](architecture/css-authoring.md)
- [Tailwind integration](architecture/tailwind-integration.md)
- [Component distribution](architecture/component-distribution.md)
- [Frontend extensibility and complex-screen acceptance](architecture/frontend-extensibility.md)
- [Component identity, page epochs and revisions](architecture/identity-and-revisions.md)
- [Threat model](security/threat-model.md)
- [Documentation style guide](documentation/style-guide.md)
- [AI-assisted developer-experience criteria](ai-dx/evaluation.md)
- [AI-DX evaluation corpus](ai-dx/corpus-v0.1.md)
- [Build and test Woge](development/build-and-test.md)
- [Repository scaffold provenance](development/scaffold-provenance.md)

## Implemented API guides

- [Render safe HTML values](guides/safe-html-values.md)
- [Buffer or stream HTML](guides/stream-html.md)
- [Write a framework-neutral page use case](guides/server-host-spi.md)
- [Describe a visible update with Patch IR](guides/patch-ir.md)
- [Encode and decode fallback patch streams](guides/patch-stream-codec.md)
- [Apply a patch stream in the browser](guides/browser-replace-runtime.md)

## Performance evidence

- [Fallback client implementation baseline](performance/fallback-client-baseline.md)
- [HTML sink baseline](performance/html-sinks-baseline.md)

## Executable M0 evidence

The spikes below justify accepted decisions. Their code is evidence, not a production API or a second source of current guidance.

- [Spike lifecycle and replacement inventory](../spikes/README.md)
- [Hand-written Spring HTML baseline](../spikes/spring-html-htmx-baseline/evidence.md)
- [Typed web-reference spike](../spikes/typed-reference-model/evidence.md)
- [HTML writer strategy spike](../spikes/html-writer-strategy/evidence.md)
- [Patch framing spike](../spikes/patch-framing/evidence.md)
- [Cross-browser fallback patch runtime spike](../spikes/fallback-patch-runtime/evidence.md)
- [Native Declarative Partial Updates spike](../spikes/native-dpu/evidence.md)
- [Standards-native CSS authoring spike](../spikes/css-authoring/evidence.md)
- [Tailwind with Kotlin templates spike](../spikes/tailwind-kotlin/evidence.md)
- [Component distribution spike](../spikes/component-distribution/evidence.md)

## Planned documentation layers

1. **Quick start:** build one useful page with Spring Boot.
2. **Mental model:** relate Woge pages, regions, actions and patches to HTML and HTTP.
3. **Task guides:** solve forms, validation, streaming, live updates, styling and deployment.
4. **API reference:** list exact types, defaults and compatibility constraints.
5. **Internals:** explain protocols, generated code and adapter implementation.

Only the architecture and quality foundations exist today. The quick start begins with the walking skeleton.

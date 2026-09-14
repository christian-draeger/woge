# ADR 0040: Generate application agent guidance from public framework metadata

- Status: Accepted
- Date: 2026-09-14
- Decision owners: @christian-draeger
- Related issues: [#149](https://github.com/christian-draeger/woge/issues/149), [#74](https://github.com/christian-draeger/woge/issues/74), [#143](https://github.com/christian-draeger/woge/issues/143), [#93](https://github.com/christian-draeger/woge/issues/93)

## Context

Coding agents work more reliably when a repository-local file names the exact framework version,
selected host, supported patterns and verification commands. A handwritten copy can silently drift
from the dependency graph and human documentation. Vendor-specific prompts or agent-only APIs would
create a second product surface, while dumping every Woge document into one file would make the
important rules harder to find.

Some desired generated route and action APIs are not implemented yet. Guidance must make those gaps
explicit instead of encouraging plausible placeholder types. It must also preserve Woge's web-native
defaults: normal forms, useful no-JavaScript paths, semantic HTML, ordinary CSS and host-neutral
application code.

## Decision

The versioned Spring Boot scaffold materializer generates one application-root `AGENTS.md`. Its inputs
are the public machine-readable framework index, a reviewable Markdown template and the scaffold's
persisted properties. The generated file names the scaffold, Woge, Kotlin and Spring Boot versions,
build/JVM levels, selected WebFlux or MVC host, generated-source root and optional test-only frontend
tooling.

The framework index owns every linked canonical human guide and compile-verified example path. The
template owns concise use/avoid guidance for typed page inputs, native forms, progressive enhancement,
typed patch targets, host-neutral components and safe HTML, URL and CSS boundaries. It explicitly says
when typed route/action descriptors are unavailable and rejects invented APIs, manual CSS-selector
targets, edits to generated output and experimental native DPU syntax as dependencies.

The generator has no model dependency and creates no agent-only application API. A committed WebFlux
copy is byte-compared with fresh output by the scaffold validator. The existing Kotlin AI-DX corpus
checks metadata paths, current versions, selected host, unresolved template tokens and required safety
concepts. External consumer tests materialize both WebFlux and MVC and assert that build properties and
guidance agree.

Model-family evaluations remain recorded milestone evidence under issue #93, not nondeterministic
pull-request checks. Humans and agents receive the same compiler, tests, public docs and examples.

## Alternatives considered

- **Maintain a handwritten `AGENTS.md`:** simple initially, but versions, host selection and generated
  ownership can drift without a deterministic signal.
- **Generate from Kotlin API reflection:** cannot represent compile-time diagnostics, documentation
  intent, planned ownership or invalid patterns and requires built artifacts.
- **Create vendor-specific prompt files:** may optimize one tool, but fragments guidance and makes the
  framework appear to have model-specific contracts.
- **Expose an agent-only Woge facade:** could narrow generation choices, but would duplicate normal
  APIs and hide the compiler-guided path developers must still understand.
- **Run live models in every pull request:** useful as periodic evidence, but slow, variable, costly and
  unsuitable as a deterministic merge gate.

## Consequences

### Positive

- A fresh application immediately carries small, version- and host-matched coding guidance.
- Framework metadata, public docs, compiler examples and generated instructions fail together on drift.
- Unsupported future APIs are clearly distinguished from implemented public concepts.
- Different coding tools can consume the same plain Markdown without a Woge-specific integration.
- The guidance reinforces familiar browser behavior rather than replacing it with framework magic.

### Negative

- Template and framework-index changes require regeneration of the committed scaffold copy.
- The first generator is repository tooling; a future distributed scaffold mechanism must package or
  reproduce this generation step.
- Plain Markdown cannot enforce behavior by itself, so compiler, JVM and browser gates remain required.
- Project-specific teams may need to layer their own instructions after scaffold creation.

## Follow-up

- Run and record the first clean-consumer, multi-model-family evaluation in
  [#93](https://github.com/christian-draeger/woge/issues/93).
- Extend the canonical guidance only when route, action and component generators land in their owning
  M2 issues; do not predeclare their API.
- Let the future `wogeDev` scaffold workflow call the same generation boundary rather than maintaining
  another template.

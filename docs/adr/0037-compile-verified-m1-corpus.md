# ADR 0037: Verify the public M1 surface with compiler fixtures and a framework index

- Status: Accepted
- Date: 2026-09-06
- Decision owners: @christian-draeger
- Related issues: [#74](https://github.com/christian-draeger/woge/issues/74)

## Context

The reference application proves complete runtime behavior, but it is intentionally larger than the
small examples a developer or coding model needs while discovering one API. Prose-only snippets can
omit imports, dependency context or stale failure behavior. Ordinary Kotlin type errors are useful,
but their internal names and the library-owned safety guidance were not yet executable evidence.

Tools also need one version-matched place to discover Woge modules, implemented concepts, examples
and compatibility facts without guessing from filenames or scraping terminal output. That index must
not pretend that planned M2 generated descriptors already exist.

## Decision

Woge maintains `woge-m1-api-corpus` as a normal root-build consumer. Its compact production sources
compile complete examples for HTML/CSS values, buffered and streaming sinks, page/deferred-region
ports, Patch IR/codec and the Spring WebFlux, Spring MVC and Ktor adapters. Documentation links to
those sources rather than copying a second code body.

Committed negative Kotlin fixtures run through the exact compiler version in the Woge version
catalog with internal diagnostic names enabled. Tests require compilation failure, source location,
the expected diagnostic kind and expected/received concept names. Fixtures have stable Woge corpus
IDs. Where Woge owns the diagnostic, such as unsafe HTML opt-in, its message also carries a stable
searchable ID. Kotlin-owned type mismatch text remains Kotlin's responsibility.

`docs/ai-dx/woge-framework-index.json` is a schema-versioned public discovery index. A deterministic
test compares it with Gradle, npm and protocol versions, the enforced module manifest and real source
paths. Public module entries name Maven coordinates; internal/support entries do not claim published
artifacts. Planned KSP diagnostics remain linked to issues #26, #27 and #28.

The existing Webref element generator continues to prove deterministic output from its pinned input
by generating twice. Clean CI rebuilds it, while incremental local builds use Gradle's declared
inputs and cache. The corpus consumes that generated public surface in the same root gate.

## Alternatives considered

- **Keep examples only inside unit tests:** executable, but imports and assertions obscure the
  smallest application-facing shape and adapters remain spread across modules.
- **Assert diagnostic prose from documentation:** easy to write, but does not prove that invalid code
  actually fails with the selected Kotlin compiler.
- **Create a compiler plugin for custom errors:** can control every message, but is disproportionate
  before KSP-generated M2 descriptors exist and would add compiler coupling to ordinary M1 APIs.
- **Generate the index by reflection:** misses compile-time concepts and examples, requires built
  artifacts and cannot represent planned ownership clearly.
- **Add placeholder route/action descriptors for the AI corpus:** would produce implementation debt
  and misleading guidance before their owning issues settle the real API.

## Consequences

### Positive

- Every M1 public concept has short compile-verified discovery evidence with complete build context.
- Regressions in type boundaries, safety opt-ins, versions and links fail deterministic CI.
- Humans and tools share one index and one public API rather than agent-specific wrappers.
- Host-framework leakage is demonstrated by an intentionally restricted portable classpath.

### Negative

- The test suite embeds the pinned Kotlin compiler and therefore adds dependency/download weight.
- Kotlin diagnostic names may change on a compiler upgrade; the corpus must review that change rather
  than silently accepting it.
- The JSON index is committed metadata and requires a matching update when modules or versions move.
- M1 can execute only the AI-DX tasks backed by implemented APIs; interaction tasks remain pending.

## Follow-up

- Add KSP-owned route, component, region and action diagnostics in issues #26, #27 and #28.
- Reuse the framework index when generating the application-local guidance in
  [#149](https://github.com/christian-draeger/woge/issues/149).
- Run the first scored clean-consumer AI-DX evaluation in
  [#93](https://github.com/christian-draeger/woge/issues/93).

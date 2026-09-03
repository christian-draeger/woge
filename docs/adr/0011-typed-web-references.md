# ADR 0011: Generate distinct typed descriptors for web references

- Status: Accepted
- Date: 2026-09-03
- Decision owners: Woge maintainers
- Related issues: [#7](https://github.com/christian-draeger/woge/issues/7), [#8](https://github.com/christian-draeger/woge/issues/8), [#19](https://github.com/christian-draeger/woge/issues/19), [#25](https://github.com/christian-draeger/woge/issues/25), [#26](https://github.com/christian-draeger/woge/issues/26), [#27](https://github.com/christian-draeger/woge/issues/27), [#28](https://github.com/christian-draeger/woge/issues/28), [#33](https://github.com/christian-draeger/woge/issues/33)

## Context

Woge needs refactor-safe links, actions and patch targets. A function reference such as `::CartSummary` knows a Kotlin function type, but it does not identify a route schema, one keyed rendering, a region owned by that rendering or a stable protocol descriptor. A generic string or `Ref<T>` makes distinct web concepts interchangeable when their payload happens to match.

The [compile-checked spike](../../spikes/typed-reference-model/evidence.md) tested repeated instances, route/action separation, component-owned regions and invalid calls using ordinary Kotlin diagnostics.

## Decision

Code generation produces separate public descriptor kinds:

- `PageRef<Parameters>` builds an ordinary URL from typed path/query input;
- `ActionRef<Command, Result>` identifies one registered action and typed invocation;
- `ComponentRef<ComponentType, Props>` identifies a component declaration;
- `ComponentInstance<ComponentType, Props>` binds that declaration to one rendered key/input in a page epoch;
- `RegionSlot<ComponentType, RegionInput>` is a named patchable slot declared by that component;
- `RegionInstance<ComponentType, RegionInput>` binds the slot to one matching rendered component instance.

Every generated component owns a nested nominal marker type. Region slots and component instances share that marker, so Kotlin rejects binding a `TaskRow` region to a `ProjectCard` even if props or region inputs are structurally identical. Repeated instances keep the same component marker and differ by a strongly typed explicit key under the [identity contract](0010-identity-epochs-and-revisions.md).

Patch builders accept `RegionInstance`, never `PageRef`, `ComponentRef`, raw DOM ID or CSS selector. The region's input/model type remains in the descriptor so a target cannot be rendered with an unrelated payload. Routes and actions likewise remain distinct even when parameter/command classes match.

Generated names are deterministic, singular and visible to completion/hover. The initial convention is a declaration-derived PascalCase descriptor (`ProjectPage`, `CreateTaskAction`, `TaskRow`) with generated nested input/marker types and lower-camel region properties (`TaskRow.status`). Generation sorts canonical declaration identities and rejects source-name, protocol-ID and route/action collisions. It does not generate ambiguous convenience overloads.

Application authors use these as ordinary values and functions. No composition receiver, remembered state, recomposition or mobile lifecycle is introduced. Generated URL values become normal `href` strings at the HTML boundary, and host adapters register the same framework-neutral descriptors.

Kotlin's normal type checker owns call-site mismatch diagnostics and already reports source location plus actual/expected shape. KSP declaration errors additionally provide a stable diagnostic identifier, violated rule, received declaration shape and minimal valid form. Positive and negative fixtures remain in the AI-DX corpus.

Raw URLs and application-owned DOM/JavaScript remain available at explicit web escape hatches, but they cannot masquerade as typed Woge routes or targets. Host-specific extension descriptors stay in adapter modules.

## Alternatives considered

- **Raw function references:** rejected as the public reference model because they omit generated route/protocol identity, rendered instance, keys and region ownership.
- **One generic `Ref<T>`:** rejected because page, action, component and region values with the same payload become interchangeable.
- **Strings for routes and CSS selectors for targets:** rejected as the normal API because refactors, ownership and active-page validation become runtime-only.
- **Component type equals rendered instance:** rejected because repeated and keyed renderings of one declaration cannot be addressed safely.
- **Structural region typing by payload only:** rejected because two unrelated components often render the same view type.
- **Compose-style receiver/state model:** rejected because typed references need nominal values, not recomposition or a new UI lifecycle.
- **Encode every HTML/CSS concept as generated Kotlin types:** rejected because descriptors solve Woge-owned routing/patch identity; normal browser attributes/classes/styles remain web strings.

## Consequences

### Positive

- Invalid page/action/target and cross-component combinations fail during compilation.
- Repeated keyed instances remain expressive without generating per-instance declarations.
- IDE completion exposes one canonical descriptor path and deterministic names help coding models.
- Host adapters, URL builders and patch IR share one framework-neutral registry vocabulary.
- The normal mental model remains page URL, form action, component instance and named HTML region.

### Negative

- Generated APIs contain generic marker types that can make error text verbose.
- KSP naming and diagnostics become compatibility-sensitive public tooling.
- Applications must provide typed keys for repeated patchable content.
- Explicit escape hatches are more ceremony than raw strings.

## Follow-up

- Settle annotation/declaration syntax and implement deterministic KSP output in [#26](https://github.com/christian-draeger/woge/issues/26), [#27](https://github.com/christian-draeger/woge/issues/27) and [#28](https://github.com/christian-draeger/woge/issues/28).
- Implement opaque rendered-instance/region IDs without exposing canonical keys in [#25](https://github.com/christian-draeger/woge/issues/25).
- Use `RegionInstance` in Patch IR and multi-target update APIs in [#19](https://github.com/christian-draeger/woge/issues/19) and [#33](https://github.com/christian-draeger/woge/issues/33).
- Preserve the spike's negative cases as stable compile-testing fixtures with KSP diagnostic IDs in [#74](https://github.com/christian-draeger/woge/issues/74).
- Revisit only surface names after the first complete M1/M2 consumer examples; retain descriptor-kind and ownership separation unless superseded by new evidence.

# ADR 0049: Generate region descriptors from annotated HTML functions

- Status: Accepted
- Date: 2026-10-09
- Decision owners: Woge maintainers
- Related issues: [#26](https://github.com/christian-draeger/woge/issues/26), [#25](https://github.com/christian-draeger/woge/issues/25), [#171](https://github.com/christian-draeger/woge/issues/171), [#27](https://github.com/christian-draeger/woge/issues/27), [#28](https://github.com/christian-draeger/woge/issues/28)
- Refines: [ADR 0011](0011-typed-web-references.md)

## Context

A deferred region replaces one element on the page. Until now the application named that element
itself, for example `identity.root.region(IdentityName.of("summary"))`. That works, but nothing stops
two places from using different names for the same region, or from sending a summary patch with the
wrong content.

[ADR 0011](0011-typed-web-references.md) decided that code generation should produce typed
descriptors for regions. This ADR settles the declaration syntax and the generated shape for regions
and components. Routes and actions follow in [#27](https://github.com/christian-draeger/woge/issues/27)
and [#28](https://github.com/christian-draeger/woge/issues/28).

## Decision

**1. A region is an annotated HTML function.** The developer writes a normal top-level
`HtmlWriter` extension with exactly one input parameter and marks it with `@WogeRegion`:

```kotlin
@WogeRegion
internal fun HtmlWriter.cartSummary(cart: Cart) { /* normal HTML */ }
```

The KSP processor in `woge-ksp` generates `CartSummaryRegion` in the same package. The name is the
function name in PascalCase with one `Region` suffix. The descriptor carries the input type, so
`CartSummaryRegion.target(page).render(cart)` only accepts a `Cart`.

**2. Targets come from the page, never from strings.** `PageRegion.target(page)` and
`KeyedRegion.target(page, key)` derive the opaque ID through the [identity contract](0010-identity-epochs-and-revisions.md)
and [#25](https://github.com/christian-draeger/woge/issues/25). The identity name is the fully
qualified function name, so equal function names in different packages never collide. Addressing the
same region (and key) twice on one page fails with a message that names the region.

**3. Components group repeated regions.** A class marked `@WogeComponent` may mark one constructor
parameter with `@WogeKey`. A region declared with `@WogeRegion(component = TaskRow::class)` then
generates a `KeyedRegion` whose `target(page, key)` requires that key type. Supported keys are
`String`, `Long`, `Int`, `UUID` and value classes wrapping one of them.

**4. The descriptor itself owns its component.** ADR 0011 proposed separate `ComponentInstance`,
`RegionSlot` and `RegionInstance` values that are bound together at runtime. A generated descriptor
already knows its component, so one value is enough: there is nothing to bind to the wrong owner.
This removes three public types without losing the ownership guarantee. Nested components are not
supported in this first version.

**5. One file per region, no registry.** Each generated file depends only on the region's own source
file and its component's source file (`aggregating = false`). KSP incremental processing therefore
deletes exactly the outputs of changed or removed declarations. There is no aggregate registry that
can go stale.

**6. Declaration errors are stable and located.** Every rule has an ID `WOGE-REF-001` to
`WOGE-REF-008`. The message names the rule, the received declaration and the valid form, and KSP
places it on the offending declaration. A clash with a hand-written class of the same name is left to
the Kotlin compiler's redeclaration error.

**7. Generated visibility follows the source.** If the function, its input types or its component
are `internal`, the descriptor is `internal`. Anything `private` is rejected, because generated code
in another file could not call it.

## Alternatives considered

- **Keep string names:** rejected; renaming a function would silently break the target and payload
  type would not be checked.
- **Aggregate registry object per module:** rejected; it needs aggregating dependencies and is the
  usual source of stale or slow incremental builds.
- **Annotations named `@Region`/`@Component`:** rejected; they clash with Spring's `@Component` in
  the most common host.
- **Runtime reflection over annotations:** rejected; errors would appear at request time instead of
  build time, and reflection is unfriendly to native images.
- **Separate slot and instance types (ADR 0011 shape):** deferred; one owner-aware descriptor gives the
  same safety with less API.

## Consequences

### Positive

- Region names, payload types and keys are checked by the compiler.
- Applications write normal HTML functions; the generated code is a small readable object.
- Incremental builds stay correct without a registry.
- People and coding assistants get the same stable, located diagnostics.

### Negative

- Applications that use typed regions need the KSP Gradle plugin.
- The generated name and the identity name are public contracts: renaming a function changes both.
- Nested components need a later extension.

## Follow-up

- Register the processor automatically through the Woge Gradle plugin and support it in `wogeDev`
  in [#171](https://github.com/christian-draeger/woge/issues/171).
- Generate typed route and action descriptors in [#27](https://github.com/christian-draeger/woge/issues/27)
  and [#28](https://github.com/christian-draeger/woge/issues/28).
- Revisit nested components when the first real use case appears.

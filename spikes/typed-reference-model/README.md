# Typed reference model spike

This compile-checked spike tests the smallest type vocabulary needed to replace route and patch-target strings. It is evidence for ADR 0011, not production API source.

Run:

```shell
./spikes/typed-reference-model/validate.sh
```

The script reuses the repository's existing Gradle wrapper, runs three positive tests and proves that three negative fixtures fail with Kotlin type diagnostics. See [evidence.md](evidence.md) for the result and trade-offs.

## Web concept map

| Familiar web concept | Proposed Kotlin descriptor |
| --- | --- |
| Page route plus path/query values | `PageRef<Parameters>` and `href(parameters)` |
| Server action plus submitted command | `ActionRef<Command, Result>` and `invoke(command)` |
| Reusable component definition | `ComponentRef<ComponentType, Props>` |
| One keyed component in this document | `ComponentInstance<ComponentType, Props>` |
| Named patchable place declared by a component | `RegionSlot<ComponentType, RegionInput>` |
| That region on one rendered component instance | `RegionInstance<ComponentType, RegionInput>` |

These are ordinary typed values. They do not introduce recomposition, remembered state or a Compose-style runtime.

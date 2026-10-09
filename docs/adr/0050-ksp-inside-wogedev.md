# ADR 0050: Run the Woge KSP processor inside wogeDev

- Status: Accepted
- Date: 2026-10-10
- Decision owners: Woge maintainers
- Related issues: [#171](https://github.com/christian-draeger/woge/issues/171), [#26](https://github.com/christian-draeger/woge/issues/26)
- Refines: [ADR 0049](0049-generated-region-descriptors.md), [ADR 0039](0039-versioned-external-spring-boot-application-scaffold.md), [ADR 0041](0041-orchestrator-owned-spring-reload-and-sse-channel.md)

## Context

[ADR 0049](0049-generated-region-descriptors.md) generates a typed descriptor for every
`@WogeRegion` function. Developers should not have to wire the processor by hand, and the edit loop
from [ADR 0041](0041-orchestrator-owned-spring-reload-and-sse-channel.md) must stay correct when an
edit changes generated code: a new region, a renamed function or a changed input type.

Generated code can also go stale. KSP's incremental mode tracks which source files each output
depends on. If that list is too short, an edit to a type can leave old generated code behind that no
longer compiles.

## Decision

**1. The application applies KSP; the Woge plugin adds the processor.** The application lists
`id("com.google.devtools.ksp")` next to its Kotlin plugin. When that plugin is present,
`dev.woge.spring-boot` adds `ksp("dev.woge:woge-ksp:<woge version>")` with the matching Woge
version. The Woge plugin itself stays free of KSP and Kotlin plugin dependencies, so applications
choose the KSP version that matches their Kotlin version.

**2. wogeDev regenerates through the normal Gradle build.** The inner build already runs
`classes`, which includes `kspKotlin`. Every Kotlin edit restarts the application, as before, so new
or changed descriptors are always loaded. KSP's incremental mode keeps unrelated regions untouched.

**3. Every generated file depends on all project sources that shape it.** That is the region
function, its component, and project types used as input or key, for example a value class that wraps
the key. Library types are not files in the project and are not tracked. Editing any of these files
reprocesses exactly that region.

**4. Stale generated code is detected and repaired once.** When a compile error points into
`build/generated/`, wogeDev reports it as `WOGE-GENERATED-CODE` with the generated file location and
reruns the build once with `-Pksp.incremental=false`. If the full regeneration succeeds, the session
continues normally. If not, the error is real and stays visible.

**5. Processor errors are located diagnostics.** KSP prints `e: [ksp] File.kt:6: WOGE-REF-002 …`.
wogeDev turns that into a diagnostic with code `WOGE-REF-002`, the source path and line, and keeps
the `Received:` and `Valid:` hints in the summary.

**6. The scaffold applies KSP and uses KSP's output directory.** The generated-source root changes
from the reserved `build/generated/sources/woge/main/kotlin` to `build/generated/ksp/main/kotlin`,
where KSP writes. The scaffold version becomes 0.3.0 and `kspVersion` is checked against the
repository's version catalog.

## Alternatives considered

- **Bundle and apply KSP from the Woge plugin:** rejected for now; the plugin would pin a KSP
  version and pull the Kotlin Gradle plugin into its own classpath.
- **Always run KSP non-incrementally:** rejected; slower on every edit for a rare failure.
- **Delete `build/generated` before each build:** rejected; it forces full recompilation.
- **A separate generation step in wogeDev:** rejected; Gradle already orders `kspKotlin` before
  `compileKotlin` and caches it.

## Consequences

### Positive

- One plugin line enables typed regions; versions cannot drift between Woge and its processor.
- Structural edits regenerate, restart and refresh the browser with no manual step.
- Stale output repairs itself or shows a located message instead of a confusing compiler error.

### Negative

- The scaffold gains one Gradle plugin and a small KSP step in each build.
- Applications that do not want generated regions still run an almost empty KSP task unless they
  remove the plugin.

## Follow-up

- Typed routes and actions ([#27](https://github.com/christian-draeger/woge/issues/27),
  [#28](https://github.com/christian-draeger/woge/issues/28)) use the same processor and the same
  wogeDev path.

# Todo DSL friction audit

Recorded 2026-10-20 for #266, before the 1.0 freeze in #62. The standalone Todo consumer exposed a
real validation problem: preparing native/enhanced alternatives reused an identity registry, and
the workaround required extracting a prepared patch, resuming the epoch and rendering again.
The [canonical notes implementation](../../examples/reference-application/shared/src/main/kotlin/dev/woge/example/notes/NoteValidation.kt)
now uses request-owned `render.validation(...) { replace(...) }`. The published-alpha Todo example
keeps its older path until this additive API is released.

## Measured change

Compare `Notes.kt` + `NoteValidation.kt` at baseline `95643dc48a3ab28b1c319d7a513a3ed27654f905`
with this change. Count all imports and nonblank/noncomment source lines, including declarations;
the route file is unchanged and remains one additional file with two host bindings.

| Measure | Before | After |
| --- | ---: | ---: |
| Source files in this comparison | 2 | 2 |
| Nonblank/noncomment source lines | 182 | 177 |
| Distinct `dev.woge.*` imports | 43 | 42 |
| Native/enhanced validation patch preparation | Extract revision/interaction, resume identity, render twice | One request-owned helper, one replacement render |
| App JavaScript introduced | 0 | 0 |

This is a narrow reduction; it is not a claim that Woge saves application lines overall. The
[complexity gradient](complexity-gradient.md) still reports the broader setup, bindings and concepts.
The safe `FormErrors()` initial/success value also removes empty-container boilerplate without
implicitly preserving submitted secrets. The native validation gradient task uses that default.

## Other surfaces assessed

- Typed action/form binding already connects command properties to serialized fields. `SerialName`
  still requires explicitly supplying the serialized name to the field API; document it in consumer
  guidance rather than guessing serializer mappings from arbitrary property references.
- Existing field/summary/error helpers preserve explicit HTML IDs/labels. Keep those relationships
  visible and test them; no alternate form DSL or accessibility-name implementation is needed.
- The shipped client needs no application asset build. Regions and assets are typed/existing APIs;
  this consumer did not justify new aliases, operator syntax or another receiver scope.
- Host registration is still four explicit bindings. It is small, visible and covered by the host
  TCK; automatic discovery would add a different ownership contract without a demonstrated gain.

## Verification and limits

Focused tests cover nonzero request revisions/interaction, independent native document collection,
limits/status rejection and safe empty defaults. API dumps, compiler corpus and the full repository
gate cover compatibility. The reference browser gate passed in the first CI run. No new overload
family or generated binding is added; incremental compiler/overload cost has not been isolated in a
controlled measurement. Human readability/usability and model-family results remain owned by #49/
#181/#272. Keep those separate from deterministic compilation success.

# Describe a visible update with Patch IR

Patch IR describes what should change in an already open HTML document. It is not a DOM API and it is
not the bytes sent over HTTP. Spring MVC, Spring WebFlux, Ktor and a future native browser path can all
adapt the same value.

Only one operation exists today: replace the children of one known Woge region.

## Build a replace patch

```kotlin
val patch =
    ReplacePatch(
        patchId = PatchId.of("patch-42"),
        target =
            PatchTarget(
                pageEpoch = PageEpoch.of("nM9yQ_2aB7"),
                region = RegionTargetId.of("project-summary-7zA"),
            ),
        interactionSequence = InteractionSequence.of(12),
        revision = TargetRevisionStep.after(TargetRevision.of(4)),
        html =
            patchHtml {
                element("p") {
                    text("3 open tasks")
                }
            },
    )
```

This says: patch `patch-42` belongs to one document, targets one registered region, answers browser
interaction 12, advances that region from revision 4 to 5 and replaces its children with the rendered
paragraph.

Most application code will not construct epochs and region IDs manually. Generated component and
region references will do that later. The explicit example shows the complete browser contract and is
useful in protocol tests.

## Why the target is not a selector

`RegionTargetId` accepts an opaque generated value, not `#summary`, `.card` or
`[data-project="42"]`. CSS remains for styling and normal browser code. A patch target instead resolves
through the active page's Woge region registry and must match exactly once.

The page epoch prevents a response from an old navigation from updating the current document. Neither
the epoch nor the target ID is a permission: every server request still performs authentication and
domain authorization.

## Why interaction and revision are separate

The interaction sequence says which user intent is newest. Imagine a search for `ko` starts as 11,
then a search for `kotlin` starts as 12. If 11 finishes last, the browser can ignore it.

The target revision says which DOM state the result builds on. A replace patch must advance exactly
from `base` to `base + 1`; duplicates, gaps and overflow fail while constructing or applying it. These
numbers coordinate the view only. They are unrelated to database optimistic-lock versions.

## HTML safety has two layers

`patchHtml` uses the normal Woge HTML DSL. Dynamic text is escaped, so `text(userInput)` cannot close an
element or inject markup. The result is materialized because one atomic length-prefixed patch frame
needs to know its payload size.

Context encoding does not make every deliberate HTML element safe for DOM insertion. The upcoming
encoder and browser runtime also reject script elements, inline `on*` handlers, `srcdoc` and dangerous
active URL schemes. An explicitly opted-in raw HTML value is still subject to that patch-sink policy.

## What is deliberately missing

There is no string operation name and no generic map of extra fields. Append, remove and live-region
announcement will appear only after their ordering, focus, accessibility and recovery behavior is
specified and tested.

There is also no JSON or binary encoding in the IR. Issue #20 maps this semantic value to canonical
metadata and the version-1 length-prefixed fallback stream. A checked-in golden fixture already proves
the IR's field order and rendered content are deterministic; it is not yet the public wire format.

See the executable [`PatchTest`](../../modules/woge-protocol/src/test/kotlin/dev/woge/protocol/PatchTest.kt)
for valid values, invalid selectors, revision gaps, protocol mismatch and fixture serialization.

# Describe a visible update with Patch IR

Patch IR describes what should change in an already open HTML document. It is not a DOM API and it is
not the bytes sent over HTTP. Spring MVC, Spring WebFlux, Ktor and a future native browser path can all
adapt the same value.

Three operations are supported: replace a known region's children, append one identified collection
item and remove one identified item.

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

Most application code uses generated region descriptors and `RegionTarget.target` instead of
constructing region IDs manually. The explicit example shows the complete browser contract and is
useful in protocol tests.

## Append and remove collection items

```kotlin
val itemId = PatchItemId.of("task-42")
val append = AppendPatch(
    patchId = PatchId.of("append-42"),
    target = collection.target,
    interactionSequence = activeInteraction,
    revision = TargetRevisionStep.after(currentRevision),
    item = patchItem(itemId, elementName = "li") {
        text("Review the task")
    },
)
val remove = RemovePatch(
    patchId = PatchId.of("remove-42"),
    target = collection.target,
    interactionSequence = activeInteraction,
    revision = TargetRevisionStep.after(nextRevision),
    itemId = itemId,
    focusTarget = collection.target.region,
)
```

`collection` is a typed region target. Render its ordinary `ul`, `ol` or other container with
`tabindex="-1"` when it is the removal focus fallback. `patchItem` supplies one root and its
`data-woge-item` identity; normal HTML and CSS attributes remain visible.

Items are identified among the collection's direct children. Appending an existing ID retains that
item unchanged; removing an absent ID changes nothing. Both still advance the collection revision.
Repeated or out-of-order frames are rejected before this item-level deduplication.

Remove moves focus to the explicitly named, registered region only when the removed item owned
focus. The fallback must not be inside the item and must be focusable. Otherwise the browser rejects
the operation before removing content. Focus elsewhere stays unchanged; nested regions in the
removed item are unregistered.

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

The target revision says which DOM state the result builds on. Every patch must advance exactly
from `base` to `base + 1`; duplicates, gaps and overflow fail while constructing or applying it. These
numbers coordinate the view only. They are unrelated to database optimistic-lock versions.

## HTML safety has two layers

`patchHtml` uses the normal Woge HTML DSL. Dynamic text is escaped, so `text(userInput)` cannot close an
element or inject markup. The result is materialized because one atomic length-prefixed patch frame
needs to know its payload size.

Context encoding does not make every deliberate HTML element safe for DOM insertion. The
encoder and browser runtime also reject script elements, inline `on*` handlers, `srcdoc` and dangerous
active URL schemes. An explicitly opted-in raw HTML value is still subject to that patch-sink policy.

## What is deliberately missing

There is no string operation name, generic map of extra fields, arbitrary selector, attribute diff
or generic announcement operation. The three operations have explicit typed values and rules.

There is also no JSON or binary encoding in the IR. The [patch stream codec](patch-stream-codec.md)
turns these values into the version-1 wire format. A checked-in golden fixture proves the IR's field
order and rendered content are deterministic.

See the executable [`PatchTest`](../../modules/woge-protocol/src/test/kotlin/dev/woge/protocol/PatchTest.kt)
for valid values, invalid selectors, revision gaps, protocol mismatch and fixture serialization.
[CollectionPatchTest](../../modules/woge-protocol/src/test/kotlin/dev/woge/protocol/CollectionPatchTest.kt)
and [ADR 0059](../adr/0059-identified-collection-patches.md) cover collection identity and removal.

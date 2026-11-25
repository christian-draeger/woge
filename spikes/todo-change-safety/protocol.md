# Todo change-safety corpus v1

This study follows the [comparison contract](../../docs/performance/comparison-contract.md)
and scopes [#291](https://github.com/christian-draeger/woge/issues/291) to ordinary Todo route,
form, list and validation edits. Use the pinned standalone Woge, KTE/htmx and Datastar sources
from the independent deployment study. Work on disposable copies. Add separate Next.js
TypeScript and generated Hilla form-contract probes for the field-type edit.

| Case | Observable obligation after the edit |
| --- | --- |
| `unchanged` | An unchanged app passes every selected browser obligation. |
| `rename-private` | An internal route helper rename preserves the public filter URL and result. |
| `route-parameter` | The Completed link continues using the parsed filter and shows zero active rows. |
| `field-rename` | Renaming a declared input coherently preserves native Add and its redirect. |
| `field-add` | The new required field and form agree. A valid legacy-compatible submission still succeeds. |
| `field-remove` | Existing native Add keeps supplying the title when removing a form field. |
| `field-type` | Client and server agree about the field type. The uncoordinated edit fails compilation or a valid Add check. |
| `action-binding` | The rendered native action points to a registered consumer. |
| `region-target` | Enhanced Add updates the board in the existing document and shows one committed row. |
| `repeated-key` | The ten starting rows have ten distinct public row identities. |
| `optional-value` | Opening the optional-filter route with no query still renders the initial board. |
| `output-context` | A title containing ordinary markup punctuation is displayed entirely as text. |
| `invalid-remote-value` | Reject a whitespace-only title and keep the existing state. |
| `unhandled-outcome` | A rejected enhanced Add makes validation visible and focuses the error summary. |

The fixtures represent incomplete, ordinary source edits. Variants need equivalent user-visible
obligations. Each implementation uses its own real bindings and rendering paths. A failing
compiler or generator prevents a candidate from reaching the browser check. Only compare
external observations for variants that build. Keep unrelated compiler or setup failures
separate. The oracle reads document state, HTTP results and focus independently of app code.

Record the first detector and the precise stage: application Kotlin, KSP or KTE generation,
TypeScript, native browser behavior, or enhanced browser behavior. The browser gate is optional
for this comparison. Report which compiled defects would pass without it. The separate Hilla
and Next.js probes compare type-checking with transpilation-only acceptance; they constrain
conclusions to one DTO field and the exact configuration. Keep cross-engine, authorization,
server concurrency, stale-region protocol ordering and human diagnostic usefulness outside this
small cross-application corpus. Cite existing Woge assertions for protocol ordering without
calling that aspect matched.

A separately declared remote-value extension supplies a previously available `archived` view
choice to the current application. The default fixture must reject that stale serialized choice
with HTTP 400. An incomplete allowlist edit that lets the service attempt to render the unknown
view must be detected. This exercises a stale submitted choice, separate from protocol revision
ordering and access control.

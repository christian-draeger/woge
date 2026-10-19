# ADR 0114: Defer WebMCP support for managed action forms

- Status: Accepted
- Date: 2026-10-20
- Decision owners: @christian-draeger
- Related issues: [#274](https://github.com/christian-draeger/woge/issues/274)

## Context

An isolated [prototype](../../spikes/consumer-testing/webmcp.mjs) annotates only the Todo add form,
retains human review and uses real Chrome 153.0.8010.12 WebMCP with explicit local flags. It is never
served by the application. The browser exposes only the business `title` input, excludes hidden
filter/plumbing, retains one tool after Woge form replacement and removes it when the form disappears.
No polyfill supplies this evidence.

Real `document.modelContext.executeTool` fills the visible form. Confirming it executes the normal
authorized Woge mutation and updates the DOM, but the browser rejects the tool result: the action
client calls `preventDefault()` without WebMCP `respondWith()`. Reporting that as a successful tool
call could cause an agent to retry an already completed mutation.

## Decision

Do not expose managed Woge action forms through built-in WebMCP annotations yet. Keep this as
negative, executable evidence. No automatic action publication, core dependency, polyfill or runtime
server MCP is added. Native and ordinary enhanced forms retain their current behavior.

A later continuation must first provide a bounded action-completion contract connecting applied
patches, validation, denied/uncertain outcomes, cancellation and tool results. DOM text observation
alone is not that contract. A native-navigation-only experiment can be separate and must preserve
confirmation/security/replay behavior. Browser flags/trial availability do not imply cross-browser
support. [Chrome's form API](https://developer.chrome.com/docs/ai/webmcp/declarative-api) remains experimental.

## Alternatives considered

Adding two form attributes alone produces a visible mutation with an error response. Treating it as
success, intercepting fetch globally or replaying a POST would hide the problem. A second action
executor would duplicate the established authorization/validation path.

## Consequences

The prototype tests schema, actual invocation, patch/removal lifecycle and visible mutation. It
records the rejected result explicitly. Full denied/failure/navigation/tool-result trials and actual
model task success are not claimed. No public WebMCP compatibility promise follows. Development MCP
remains a separate, already implemented workflow.

## Follow-up

Track the missing completion/result contract in #274 before expanding the browser lifecycle/security
matrix or running model trials. Preserve this real rejection so later work cannot mistake a DOM
mutation for a successfully acknowledged tool call.

# Keep server-rendered data current

Start with a GET that renders current application data and forms that mutate it on the server.
The [Todo consumer](../../examples/todo-spring-boot/README.md) adds enhancement to that same flow.

## Decide who owns each kind of state

| State | Owner and representation |
| --- | --- |
| Todo title/completed flag | Application repository on the server |
| Active/completed filter | Typed page URL and ordinary links |
| Text not yet submitted | Native input in this browser tab |
| Current list/count | Server-rendered board region |
| Render epoch, target revisions and interaction sequence | Woge ordering metadata |
| Temporary state inside an opted-in island | That island's bounded root |

Kotlin input and region descriptors do not make remote values trusted. Decode bounded requests and
apply authentication/authorization in the server application. The public Todo demo deliberately
allows anonymous visitors and has one shared, process-local store.

## After a mutation

One action changes application state and returns typed replacements through
`context.render.updates(canonicalUrl) { replace(BoardRegion, snapshot) }`.
Native submissions receive a 303 redirect to that URL. Enhanced submissions receive patches.
The Todo board contains list and count together, so one snapshot renders both.

The add form is outside the live board. Another visitor's update therefore cannot replace a local
draft. Explicit form validation replaces the form/error region and focuses its document-owned
summary; it does not add a second application handler.

Use the actual generated region and route names from your application. The complete
[Todo actions](../../examples/todo-spring-boot/src/main/kotlin/example/todo/TodoActions.kt) target the
published alpha API. The request-owned [validation helper](typed-actions.md) is available in this
checkout after ADR 0110 and must not be assumed to exist in an older artifact.

## When another client changes data

`LiveRegionPage` maps one typed page URL to document rendering, authorized refresh GETs and SSE.
The server sends a region invalidation. The client fetches current HTML, using that tab's epoch,
target revision and interaction sequence. It does not retain a separate graph of Todo objects.

The live client serializes refreshes per target and coalesces a burst into at most one follow-up.
Reconnect reauthorizes and resynchronizes. The live event stream is not a durable replay log.
Keep loaders side-effect-free, and enforce the same access rule on the document, refresh and every
subscription/reconnect. See [live regions](live-updates.md).

Choose live-region boundaries around interactions deliberately. Replacing a button between pointer
down and pointer up can cancel its activation before any POST, just as ordinary HTML replacement
does. Keeping the add form outside the board protects its draft; it does not preserve every
interaction inside the board. The [three-engine comparison](../../spikes/consumer-testing/results.md#bounded-runner-follow-up-2026-10-20)
records this limitation. Do not compensate by replaying a mutation; further interaction-preservation
policy is tracked in [#276](https://github.com/christian-draeger/woge/issues/276).

## Ordering and recovery

| Situation | Supported outcome |
| --- | --- |
| A newer interaction completes before an older result | Superseded/stale patches cannot overwrite the newer target state |
| Many invalidations arrive during one GET | One running refresh plus at most one pending follow-up per target |
| A refresh fails | Report the failure; later invalidations can retry the safe GET |
| SSE disconnects | Reconnect/resync, with fresh authorization |
| Enhancement cannot start | Native page, forms and navigation remain usable |
| A mutation response is lost | The mutation may have committed; inspect current data through a safe GET |
| JavaScript is disabled | POST/Redirect/GET and filter links; reload to see other visitors' changes |

A request UUID alone does not make a mutation idempotent. Woge never automatically replays an
uncertain POST. An application's durable reservation/idempotency policy is explicit and scoped;
see [typed actions](typed-actions.md) and [failure/recovery](../architecture/failure-and-recovery.md).

## Caching and local widgets

Server render caching has its own explicit input, identity and authorization-scope rules; see
[component caching](component-caching.md). Ordinary HTTP caches retain their normal semantics;
personalized/non-cacheable HTML is not a shared cache entry.

A data-heavy island may use a local query cache when its interaction needs one. It owns its internal
DOM and local data representation; Woge must not simultaneously replace the same island-owned
content. Define the invalidation/auth scope and authoritative reconciliation for optimistic changes.
There is no general client query engine or offline data store in core Woge.

## Executable evidence

- [Todo HTTP tests](../../examples/todo-spring-boot/src/test/kotlin/example/todo/TodoApplicationTest.kt):
  native redirects, preserved validation, denied cross-origin writes and real decoded patch outcomes.
- [Todo browser journeys](../../examples/todo-spring-boot/browser-tests/todos.spec.mjs):
  equivalent native/enhanced mutations, focus/error recovery and two-tab updates preserving a draft.
- [Live client contract](../../client/woge-fallback-client/tests/live.test.mjs):
  serialized/coalesced invalidations and recovery after a failed refresh.
- [Reference browser journeys](../../client/woge-fallback-client/reference-browser-tests):
  revision/intent rejection and browser-owned state through the real hosts.

Run the Todo tests as described in its README. The framework's existing browser/TCK suites remain
the host/protocol conformance gate; one demo is not an application-independent concurrency proof.

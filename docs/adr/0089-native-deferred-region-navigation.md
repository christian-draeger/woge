# ADR 0089: Native navigation for deferred regions

- Status: Accepted
- Date: 2026-10-11
- Decision owners: Woge maintainers
- Related issues: [#216](https://github.com/christian-draeger/woge/issues/216), [#148](https://github.com/christian-draeger/woge/issues/148)

## Context

The complexity gradient's deferred forecast only delivered its result through the browser client.
With JavaScript disabled it stayed on loading text. This violates
[ADR 0007](0007-browser-support-and-progressive-enhancement.md) and the native navigation requirement
in [ADR 0027](0027-fetch-deferred-patches-after-html-shell.md).

A patch stream is not an HTML document. Its epoch and region IDs describe a rendered document; they
are not credentials and should not be needed to navigate to the data. The shipped client is already
close to its fixed size ceiling, and normal HTML can solve this problem without adding client code.

## Decision

An application that renders deferred loading HTML must also provide a native path to the result.
Unless the initial document already contains that result, the loading content includes an ordinary
link to an application-owned, safe GET that renders a complete HTML page. A page-level link to a
complete document is also valid when several regions share one fallback.

Use the existing typed HTML DSL (`a`, `url`, optionally `noscript`), typed page routes and
`handlers.page`. Do not add a deferred-specific public API, endpoint registry, protocol mode or
client behavior. `regionPlaceholder` preserves application loading HTML but cannot invent routes,
loaders or access rules. This is the same contract on Spring MVC, Spring WebFlux and Ktor.

The preferred link is inside `noscript`: it is available with scripting disabled and the browser
ignores it during enhancement. A plain visible link is appropriate when the application also wants
to cover failure to load an enabled script. Neither option requires client JavaScript or CSS.

The normal page route may accept a typed query option such as `complete=true`. It runs the same
access checks as the shell, waits for the same read-only loader and calls the same typed region
renderer in a complete document. That mode must not emit loading placeholders or start the deferred
client again. It preserves ordinary headings, language, navigation and styles. The enhanced shell
and patch transport remain unchanged.

The native URL contains only ordinary page input, not a render epoch, opaque region ID, secret or
access token. Direct requests and reloads must be safe. Authentication and authorization run before
loading data in shell, native and patch modes. Copying a URL or adding render parameters must not
grant access; all modes use the host's established identity. GETs never execute mutations or actions.

## Alternatives considered

- **Automatically stream final HTML into every initial document:** valid for applications that choose
  it, but not the default. Waiting for all regions removes the immediate shell's latency benefit;
  portable baseline browsers cannot replace arbitrary existing elements without a client.
- **Navigate to the patch stream:** rejected; protocol bytes are not a usable native document.
- **Add a fallback URL parameter or a new page/region handler abstraction:** unnecessary. The typed
  loading DSL and ordinary page handler already express links, page modes and access checks.
- **Add client code to hide fallback links:** rejected; `noscript` already provides that behavior.

## Consequences

- Native users can reach the same data through inspectable HTML and safe HTTP navigation.
- Existing APIs, adapter transports and the client bundle are unchanged; no byte ceiling is raised.
- Applications own the complete page, link labels, shared loader and authorization. The framework
  cannot verify a link's endpoint at compile time, so guide examples and executable contracts matter.
- `noscript` covers disabled scripting, not a failed module when scripting remains enabled. Use a
  plain link for the latter requirement.

## Follow-up

- The complexity gradient forecast now provides `/forecast?complete=true` and tests both paths.
- The adapter TCK verifies HTML navigation, repeatable GETs, enhanced patch identity and access checks
  across all three hosts. Its optional browser script follows the native link with scripting disabled
  and applies the shipped client with scripting enabled.
- Playwright's baseline browser suite verifies native links in no-JavaScript contexts and retains
  incremental completion-order coverage for the enhanced path.
- Keep the [deferred-region guide](../guides/deferred-regions.md) aligned with this contract; do not
  replace it with a JavaScript-only loading message in future examples.

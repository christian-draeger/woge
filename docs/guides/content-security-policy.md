# Content Security Policy and Trusted Types

Woge works with external JavaScript modules and stylesheets. Native HTML and forms need no
JavaScript. Enhanced actions, patches, deferred regions and live updates do not need inline
executable scripts, event-handler attributes, `unsafe-inline` or `unsafe-eval`.

## Recommended production header

Send this HTTP response header on every document, including validation and error pages:

```http
Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self'; base-uri 'none'; form-action 'self'; object-src 'none'; frame-ancestors 'none'; require-trusted-types-for 'script'; trusted-types woge
```

This is the policy used by all three [reference hosts](../../examples/reference-application/README.md).
It assumes assets and endpoints are served from the application's origin. Woge does not install a
production CSP for your application: you own the header and any additional origins. Start with
`Content-Security-Policy-Report-Only` when rolling a policy out to an existing site, then enforce it.
A report-only policy does not block unsafe execution.

| Flow / resource | Directive | What to allow |
| --- | --- | --- |
| Native page and form | `form-action 'self'` | The origin receiving native GET/POST forms; this directive does not fall back to `default-src` |
| Enhanced action and patch | `script-src 'self'`, `connect-src 'self'` | The external client/application modules and Fetch action or region endpoints |
| Deferred region | `connect-src 'self'` | The patch-stream GET endpoint; no extra inline loader |
| Live update | `connect-src 'self'` | The EventSource/SSE endpoint **and** the region GET used after invalidation |
| CSS, including Tailwind | `style-src 'self'` | External stylesheets; no runtime Tailwind CDN or inline CSS required |
| Images | `img-src 'self'` | Same-origin images; explicitly allow an image CDN if used; `data:` is not required by Woge |
| Fonts / other assets | `font-src`, `media-src`, etc. | These inherit `default-src 'self'`; add only the origins your site actually uses |
| Document URL resolution | `base-uri 'none'` | No `<base>` element; the safe HTML DSL also rejects generic base elements |
| Embedding | `object-src 'none'`, `frame-ancestors 'none'` | No plugins or framed documents; change frame ancestors only for intentional embedding |

For a native-only page, `script-src 'none'; connect-src 'none'; trusted-types 'none'` can replace
those directives when the page truly loads no modules or live/deferred client. Keep
`require-trusted-types-for 'script'` as defense in depth where supported. CSP does not replace
CSRF checks, authorization, typed URLs or output encoding.

## Trusted Types patch parsing

On browsers with `window.trustedTypes`, the fallback client lazily creates one policy named
`woge` in the document's realm. Allow it with `trusted-types woge` together with
`require-trusted-types-for 'script'`. Replacement and append HTML pass through this private policy
before entering an inert `<template>`. The policy only implements `createHTML`: no script or
script-URL permission, no `default` policy, no application callback to weaken it. Load one copy of
the client per document; independent bundles cannot each create the same policy without permission
for duplicates. Do not pre-create the `woge` policy or add `allow-duplicates` to work around that.

The input is decoded server-produced patch HTML, not arbitrary application strings. The policy is
**not a sanitizer**: after parsing, Woge still rejects scripts, styles, event handlers, active URLs,
base elements and other blocked content before touching the live DOM. Existing protocol, epoch,
revision and region checks still apply. A denied policy fails closed; it does not fall back to raw
HTML or create a permissive default policy. Browsers without Trusted Types keep the same inert
fragment checks. Same-origin server output is not automatically safe: keep typed HTML encoding and
review explicit unsafe HTML capabilities.

Source-distributed components must retain these checks. Optional behavior (such as
`installWogeDialogs`) belongs in an external module using `addEventListener`, never `onclick`, an
inline script, a custom permissive policy or an alternate unsanitized HTML sink.

## Nonces and hashes

With the recommended same-origin external assets, **no nonce is required**. Use the existing typed
`moduleScript(applicationUrl("/assets/application.js"))` and
`stylesheet(applicationUrl("/assets/application.css"))` helpers.

If your application chooses a nonce-based policy, generate a cryptographically random value for
**each response** and put that same value in the header and the asset helper's `nonce = cspNonce(value)`
argument. `moduleScript`, `stylesheet` and `style(CssStylesheet, nonce = ...)` already support it.
Woge neither generates the nonce nor changes your production header. When using `'strict-dynamic'`,
nonce the initial external module too: supporting browsers ignore host allowlists for scripts.

An inline `CssStylesheet` needs a matching `style-src 'nonce-…'` or SHA-256/384/512 hash of its exact
CSS text (not the `<style>` tags). Prefer an external stylesheet. Nonces do **not** authorize `style`
attributes. Prefer CSS classes instead of inline declarations under this policy; if you intentionally
use attribute hashes, own the exact hashes and the `style-src-attr 'unsafe-hashes'` exception.
Woge has no unavoidable inline executable script that needs a script hash. Optional third-party
scripts must be assessed separately; never add `unsafe-inline` or `unsafe-eval` just for Woge.

## Spring Security (MVC and WebFlux)

Set the policy in the application's existing security chain. The same headers customization is
available on MVC's `HttpSecurity` and WebFlux's `ServerHttpSecurity`:

```kotlin
val policy = "default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; " +
    "img-src 'self'; base-uri 'none'; form-action 'self'; object-src 'none'; frame-ancestors 'none'; " +
    "require-trusted-types-for 'script'; trusted-types woge"

http.headers { headers ->
    headers.contentSecurityPolicy { csp -> csp.policyDirectives(policy) }
}
// Keep the rest of your authentication/CSRF rules, then return http.build().
```

Spring Security is optional. The compiled reference applications show an ordinary
[MVC filter](../../examples/reference-application/spring-mvc/src/main/kotlin/dev/woge/example/mvc/WogeMvcQuickstartApplication.kt)
and a [WebFlux WebFilter](../../examples/reference-application/spring-webflux/src/main/kotlin/dev/woge/example/WogeQuickstartApplication.kt)
setting the same header without adding a security dependency.

## Ktor

Set an HTTP header early in the application pipeline, not an inline meta policy:

```kotlin
// Inside Application.module(), with the policy string above:
intercept(ApplicationCallPipeline.Setup) {
    call.response.headers.append("Content-Security-Policy", policy)
}
```

Import `io.ktor.server.application.ApplicationCallPipeline` and `io.ktor.server.application.call`.
The [compiled Ktor host](../../examples/reference-application/ktor/src/main/kotlin/dev/woge/example/ktor/WogeKtorQuickstart.kt)
uses the same policy and browser journeys as Spring MVC and WebFlux.

## Development is different

`wogeDev` deliberately loads its external client and overlay CSS from a session loopback origin and
opens an SSE connection there. The **development-only** Spring child filters add that origin to
`script-src`, `style-src` and `connect-src`, including report-only headers, and reuse a head asset's
nonce. They do not add `unsafe-inline`, `unsafe-eval` or a Trusted Types default policy. The filters
are activated by `WOGE_DEV_CLIENT_FILE`; the development modules are not production dependencies.
`verifyWogeProductionArtifact` rejects development tooling in production jars. See
[ADR 0046](../adr/0046-development-client-under-strict-csp.md).

The optional [Vite adapter](vite.md) also needs its dev HTTP origin and WebSocket origin;
nonce-based `style-src` must allow the CSS Vite injects. Ktor does not rewrite these policies
automatically: configure exact development origins in a separate development-only configuration.
Do not copy loopback origins, injected-style allowances or Vite hot-update permissions into
production. Plain HTML/CSS and the optional prebuilt Tailwind CLI need neither Node nor Vite.

## Regression coverage

The existing `referenceBrowserSmoke` CI job runs the native/no-JavaScript, enhanced action,
deferred, live SSE and headless-component journeys with the real strict header on all three hosts.
The shared fixture fails on `securitypolicyviolation` events and console CSP/Trusted Types errors.
The `browser-contract` job also tests replace/append, multiple runtimes and rejected active markup
under that header, with Chromium enforcing real Trusted Types (other engines retain inert checks).

The reference application uses plain external CSS. There is no Tailwind reference-journey variant:
`testTailwindExample` already builds the standalone Tailwind example and verifies its content-hashed
external CSS in the production jar, in the same CI job. Its output has the same `stylesheet` delivery
and `style-src 'self'` requirement as the plain-CSS reference, not a CDN script or runtime style
injector. Changing that delivery model requires strict-policy browser coverage, not a silent policy
relaxation.

# Compatibility and upgrades

Woge is pre-1.0. Keep all Woge server artifacts, the browser package and generated code on the same
release version. All JVM artifacts use the root Gradle version. The browser client served by a server
artifact is bundled from that release. There is no Woge BOM today; Spring Boot's own dependency
platform still aligns Spring dependencies.

## Compatibility matrix

| Part | Stability before 1.0 | Supported compatibility |
| --- | --- | --- |
| Core APIs | Beta at most | Source and binary compatibility can change between releases; no N-1 binary promise. |
| Patch protocol | Beta | Adjacent minor releases interoperate on the same versioned wire format; v1 golden fixtures lock framing and fields. |
| Browser runtime | Beta | Use the runtime bundled with the matching server artifact. Adjacent releases support the same v1 protocol. |
| Spring Boot starter and MVC/WebFlux adapters | Beta | Align all Woge artifacts to one version. Rolling nodes may differ by one minor version for v1 wire traffic. |
| Ktor adapter | Beta | Same core, protocol and adjacent-minor wire policy as both Spring adapters. |
| KSP-generated code | Beta | Regenerate after upgrades. Generated source follows the processor version; no binary compatibility promise. |

`Experimental` APIs are explicitly marked and may change without notice. Every other public API remains
at most `Beta` before 1.0. `Stable` means an API has been deliberately frozen, and is reserved for
1.0 or later. Binary API checks catch accidental JVM changes; they are not a compatibility promise.

## Negotiation and safe failure

The browser sends `Accept: application/vnd.woge.patch-stream; version=1` for enhanced actions. A server
that cannot serve the requested explicit version responds with `406 Not Acceptable` and
`Woge-Protocol-Error: unsupported-version` before executing the action. The browser follows the
bounded reload path with a safe page GET; it never retries a form POST. Native forms do not request
patch-stream responses and continue to use HTML.

Patch streams announce their version in the `WOGE` preamble and patch metadata. An unknown or newer
version is rejected before that frame is applied; the browser cancels the stream and safely reloads
once for that page URL and tab. Live updates use EventSource, which cannot send a custom version
header, so the browser adds `_woge_protocol_version=1` to the live URL. An unsupported explicit value
gets 406 before a subscription opens. EventSource hides that HTTP response, so connection errors are
reported to the application's `onError` callback without changing the DOM. Its small v1 `invalidate`
and `resync` events only cause normal authorized region GETs; unknown event names are ignored.
Changes to existing v1 event meaning require a new protocol version.

## Rolling deployments

Adjacent beta versions N-1 and N interoperate on patch, action and live wire formats only while using
the same supported protocol version. This is not binary compatibility. A new node may render an
immutable `/_woge/assets/<hash>/...` URL that an old node does not have. The old node returns 404;
HTML and native forms still work without JavaScript. If enhanced pages must work throughout the
rollout, keep both asset trees available at every node or behind the shared static host until the
rollout and browser cache window have passed. Never serve a different file at an old fingerprinted
URL.

The shared version-1 fixtures in `modules/woge-protocol/src/test/resources/fixtures` are checked by
JVM and browser tests. The adapter TCK verifies that MVC, WebFlux and Ktor reject an unsupported action
protocol version without executing the action. For a breaking wire change, introduce a new protocol
version and fixtures rather than silently changing v1.

For immutable cache behavior and asset packaging, see the [production asset guide](production-assets.md).
The policy and its pre-1.0 boundary are recorded in [ADR 0077](../adr/0077-pre-1-0-compatibility-and-version-skew.md).

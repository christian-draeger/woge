# HTTP caching and conditional pages

Woge page handlers default to **`Cache-Control: no-store`**. This includes ordinary HTML, native
redirects and failures. User-specific pages do not become shared-cacheable simply because they are GETs.
Woge does not keep a server-side page cache.

To allow a page to be stored and revalidated, supply normal HTTP headers in `ResponseMetadata`:

```kotlin
htmlPage(
    metadata = ResponseMetadata(
        headers = ResponseHeaders.of(
            httpHeader("Cache-Control", "private, no-cache"),
            httpHeader("ETag", "\"board-v42\""),
            httpHeader("Last-Modified", "Sat, 10 Oct 2026 16:00:00 GMT"),
            httpHeader("Vary", "Accept-Language"),
        ),
    ),
) {
    // Render the already authorized representation.
}
```

`private` permits browser storage but excludes shared caches. `no-cache` means **revalidate before
reuse**, not "do not store". `no-store` prevents storage. Choose public caching only for content that
can safely be reused by anyone; for example `public, max-age=300`. A public cache hit may never reach
your authorization code. `Vary: Authorization` is not a substitute for private/no-store policy.

Validators belong to your application. Change an ETag whenever the selected representation changes,
including permissions, locale and enhancement-related content. Keep it quoted. Use a weak tag
(`W/"board-v42"`) if it describes equivalent content rather than identical bytes. Last-Modified is an
HTTP date at whole-second precision. Do not calculate a tag by collecting a lazy document first.

## GET, HEAD and 304

MVC, WebFlux and Ktor run the application and its authorization before considering request validators.
For an explicitly cacheable 200 HTML page:

- `If-None-Match` uses weak comparison for GET/HEAD and supports a list or standalone `*`.
- With no `If-None-Match`, `If-Modified-Since` can match Last-Modified.
- Matching conditions return **304 with no body**, preserving Cache-Control, ETag, Last-Modified and
  Vary. No document frame renders.
- Nonmatching, malformed or oversized conditions return the ordinary response. A present entity-tag
  condition always takes precedence over a date, even when it does not match.
- HEAD uses GET metadata without HTML bytes. Current authorization can still return 403 instead of 304.

Use RFC 1123 HTTP dates such as the example above. Obsolete RFC 850 and asctime request dates are
also accepted for HTTP interoperability.
Only cacheable 200 documents are revalidated; redirects and errors never become 304 through this helper.
Direct integrations outside Woge's handlers must call `result.forHttpRequest(method, tags, dates)`
after authorization and map `PageResult.NotModified` to a bodyless 304 themselves.

## Actions, cookies and negotiated content

Unsafe requests, prepared patch results, errors and responses setting cookies always become no-store,
even if the application supplies a public cache header. Their validators are removed. This protects
native action redirects and validation HTML as well as enhanced responses. A successful action's
destination GET has a separate policy. Existing deferred patch streams are also no-store.

Declare Vary for every request field that actually selects the representation, such as
`Accept-Language`. Native/enhanced action negotiation already adds `Vary: Accept`.
If a route selects protocol versions through Accept, include Accept as well. Do not cache a response
under one validator when its HTML differs by user, mode or protocol.

## Proxies and assets

Keep these headers intact at your proxy/CDN. Do not override private or no-store dynamic pages with a
global cache rule. Ordinary browser DevTools show the same HTTP headers and 200/304 behavior as any
other web application. The shared [adapter TCK](../../testing/woge-adapter-tck/README.md) runs the
conditional flow over actual HTTP in all three hosts; dedicated proxy/CDN fixtures remain open in
[#117](https://github.com/christian-draeger/woge/issues/117).

Static files stay with the host's resource handler. Only immutable, content-fingerprinted URLs should
receive `public, max-age=31536000, immutable`; unversioned URLs need a shorter lifetime or revalidation.
Woge's own production hashing and manifest integration is tracked in
[#170](https://github.com/christian-draeger/woge/issues/170). CSS/Vite/Node pipelines are not required
to choose ordinary HTTP cache headers. Component caching is a separate feature under #59.

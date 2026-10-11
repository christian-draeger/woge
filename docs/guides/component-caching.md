# Cache rendered components safely

Use server-side component caching only when a component is expensive to render and its output can be
fully described by stable inputs. The cache is opt-in; ordinary rendering and HTTP cache policy do
not change.

```kotlin
val cache = ComponentCache()

fun greeting(name: String, userId: String): String =
    cache.render(
        key = ComponentCacheKey(
            component = "greeting",
            inputs = name,
            scope = ComponentCacheScope.Principal(userId),
        ),
        options = ComponentCacheOptions(
            ttl = Duration.ofMinutes(2),
            tags = setOf("greeting-data"),
            requestIsPersonalized = true,
        ),
    ) {
        element("p") { text("Hello, $name") }
    }
```

The key includes the component name, every input that can change the markup, and a required scope.
Use `Public` only for output that is the same for every visitor. Use `Principal(id)` for user-specific
HTML or `Tenant(id)` for tenant-specific HTML. If the request is personalized, set
`requestIsPersonalized = true`; using `Public` then fails before rendering. Never put mutable input
objects in keys.

A cache miss renders once even if several requests arrive together. Entries expire after their
positive TTL. The default bounded in-memory cache keeps at most 256 entries. A custom
`ComponentCacheStorage` must declare a positive `maxEntries` and implement bounded eviction and tag
association. Call `invalidate(key)` to remove one scoped key or `invalidateTag("greeting-data")` to
remove related components after their source data changes.

For revisioned output, pass the revision being rendered. With
`staleWhileRevalidate = true`, an expired entry may be returned while one background refresh runs, but
only if its revision is at least `minimumRevision`, the newest revision the client has already seen.
If it is older, the request waits for fresh output instead. No timing sleeps are needed in tests:
inject a `Clock` and an `Executor` to control expiry and refresh execution.

A `ComponentCacheListener` receives `HIT`, `MISS`, `STALE` and `INVALIDATED` events. Events contain
the component identity only, not rendering inputs, HTML, principal IDs or tenant IDs. Observer
failures never affect rendering.

Render blocks use the typed `HtmlWriter` DSL. The cache rejects output written with `UnsafeHtml`; do
not treat trusted raw markup as cache-safe input. Cached fragments are server-side only. This feature
does not set HTTP `Cache-Control` or ETag headers; see the [HTTP caching guide](http-caching.md) for
browser and proxy behavior.

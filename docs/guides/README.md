# Guides

Task-oriented, web-first guides live here. The first executable Spring Boot guide is delivered with the M1 walking skeleton.

Canonical Kotlin examples belong in the root [`examples`](../../examples/README.md) build. Guides link to those sources instead of maintaining a second uncompiled copy.

The first implemented low-level guide is [safe HTML values](safe-html-values.md). Its temporary
canonical example is compiled with `woge-core`; it moves into the reference application once that
consumer build exists.

The [CSS and asset guide](css-and-assets.md) shows external and colocated CSS, declaration lists,
plain/CSS-Module/Tailwind class composition, head assets and explicit CSP/SRI boundaries.

The [HTML sink guide](stream-html.md) explains when to buffer or stream the same component functions.

The [server host SPI guide](server-host-spi.md) introduces typed page use cases, immutable request
facts, streamed HTML frames, redirects and safe failures before the Spring and Ktor adapters land.

The [Patch IR guide](patch-ir.md) explains the first transport-neutral replace operation and its
page, target, interaction and revision checks in browser terms.

The [patch-stream codec guide](patch-stream-codec.md) explains the version-1 byte framing, terminal
events, strict validation and host-adapter lifecycle.

The [browser Replace runtime guide](browser-replace-runtime.md) starts from normal HTML and explains
page-local regions, streamed application, delegated lifecycle events and safe failure behavior.

The [deferred-region guide](deferred-regions.md) shows ordinary loading HTML, independently completing
server work, bounded concurrency and request-owned cancellation.

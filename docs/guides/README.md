# Guides

Task-oriented, web-first guides live here. Start with the executable
[Spring Boot WebFlux quickstart](quickstart-spring-boot.md), then use its focused
[Kotlin bridge](kotlin-for-web-developers.md) when unfamiliar syntax appears.

To begin from a small standalone repository rather than the multi-host framework example, use the
[versioned Spring Boot application scaffold](../../scaffolds/spring-boot/README.md). It defaults to
WebFlux, offers an explicit MVC switch and needs no JavaScript toolchain for normal development.

Canonical Kotlin examples belong in the root [`examples`](../../examples/README.md) build. Guides link to those sources instead of maintaining a second uncompiled copy.

The first implemented low-level guide is [safe HTML values](safe-html-values.md). Its temporary
canonical example is compiled with `woge-core`; it moves into the reference application once that
consumer build exists.

The [HTML element guide](html-elements.md) maps familiar tags, attributes, text-only elements,
active raw-text boundaries and platform escape hatches to the generated Kotlin DSL.

The [CSS and asset guide](css-and-assets.md) shows external and colocated CSS, declaration lists,
plain/CSS-Module/Tailwind class composition, head assets and explicit CSP/SRI boundaries.

The [HTML sink guide](stream-html.md) explains when to buffer or stream the same component functions.

The [server host SPI guide](server-host-spi.md) introduces typed page use cases, immutable request
facts, streamed HTML frames, redirects and safe failures shared by the Spring and Ktor adapters.

The [typed page routes guide](typed-routes.md) declares a URL once on the page input, builds links
from it and lets every host read the same path and query values.

The [typed action descriptor guide](typed-actions.md) introduces stable form URLs, typed executors
and explicit registries, with the remaining adapter-dispatch scope called out.

The [native multipart upload guide](native-multipart-uploads.md) adds ordinary file inputs, bounded
text and file decoding, request-owned cleanup and the explicit Spring MVC resolver configuration.

The [HTTP caching guide](http-caching.md) explains no-store defaults, explicit page validators,
conditional GET/HEAD responses and ordinary Vary/intermediary rules.

The [production asset guide](production-assets.md) packages ordinary static files under reproducible
content-hashed URLs, preserves relative CSS references and verifies the actual production JAR.

The [Patch IR guide](patch-ir.md) explains the first transport-neutral replace operation and its
page, target, interaction and revision checks in browser terms.

The [patch-stream codec guide](patch-stream-codec.md) explains the version-1 byte framing, terminal
events, strict validation and host-adapter lifecycle.

The [browser Replace runtime guide](browser-replace-runtime.md) starts from normal HTML and explains
page-local regions, streamed application, delegated lifecycle events and safe failure behavior.

The [fallback-client installation guide](fallback-client-installation.md) covers npm bundlers, the
Node-free JVM asset, static deployment, version alignment, caching, source maps, CSP and SRI.

The [deferred-region guide](deferred-regions.md) shows ordinary loading HTML, independently completing
server work, bounded concurrency and request-owned cancellation.

The [live updates guide](live-updates.md) pushes "this region changed" notices over Server-Sent
Events; the browser reloads each region with its normal, authorized GET.

The [Ktor adapter guide](ktor-adapter.md) connects the same portable page to ordinary suspending Ktor
routes while keeping Spring Boot as the primary getting-started path.

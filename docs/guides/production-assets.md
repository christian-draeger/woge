# Production assets without Node

Put ordinary CSS, JavaScript, fonts and images in **`src/main/resources/static`**. The Woge Gradle
plugin packages them automatically when building classes or a production JAR. Plain HTML/CSS
applications need no Node, Vite or bundler.

`dev.woge.spring-boot` includes the task. Other hosts can apply `dev.woge.application`.
Run `./gradlew wogeAssets` to inspect just the generated files, or use the normal `check` / `bootJar`
workflow. `wogeTasks` describes the same workflow for humans and agents.

## Write normal URLs through the HTML DSL

Spring Boot supplies an `AssetUrls` bean when the application has a generated manifest. Inject it
into your page factory as shown in the [executable scaffold](../../scaffolds/spring-boot/src/main/kotlin/example/woge/Application.kt).
The [scaffold page](../../scaffolds/spring-boot/src/main/kotlin/example/woge/HomePage.kt) writes:

```kotlin
stylesheet(assets.url(applicationUrl("/styles.css")))
```

Use the same result with `moduleScript`, `preload`, `assetLink` or an image's typed URL attribute.
The compiler-checked [HTML/CSS corpus](../../examples/m1-api-corpus/src/main/kotlin/dev/woge/examples/m1/HtmlCssAndSinks.kt)
uses this public API too.

In production the URL looks like `/_woge/assets/<sha256>/styles.css`. In `wogeDev` it stays
`/styles.css`, so development content is not stuck behind immutable caching. Other hosts can call
`AssetUrls.load()` explicitly and serve the generated classpath resources normally. Missing
manifests, multiple application manifests and unregistered URLs fail instead of silently returning
a broken or unversioned production link.

## Relative CSS references still work

Woge hashes **one complete asset tree**, not each file independently. All files keep their directory
structure beneath the same hash. A stylesheet in `css/site.css` can still refer to
`../images/logo.svg` or import a sibling sheet without a CSS parser or rewrite step.

Any changed, added, removed or renamed file changes the tree hash. This also invalidates unchanged
files; that is a deliberate simplicity tradeoff. File timestamps and checkout location do not affect
the hash. Source files are never rewritten.

Woge percent-encodes URL path segments. Encode dynamic URL pieces before calling `applicationUrl`
as usual. Symlinks and files outside the configured static directory are rejected. Do not place
secrets in a static directory: everything there is intended to be publicly downloadable.

## HTTP and production verification

MVC and WebFlux serve generated files with:

```http
Cache-Control: max-age=31536000, public, immutable
```

Unknown hashes or paths return 404. The host's ordinary resource handler owns content types,
conditional file requests, HEAD and ranges. Original static URLs remain available, but do not
receive this immutable policy. Use the typed resolved URL in production pages rather than hardcoding
an old hash.

The deterministic manifest is packaged at `META-INF/woge/assets.properties`; the hashed tree is
under `META-INF/woge/assets/<hash>/`. `verifyWogeProductionArtifact` verifies the **actual JAR**:
its manifest must match the current build, every registered file must exist, file bytes must reproduce
the tree hash, and no stale/unregistered generated files may remain. The same task still rejects
development tooling. An empty asset directory needs no special configuration.

Keep hashed resources immutable at your proxy/CDN. Do not reuse these cache headers on personalized
HTML or action responses; see [HTTP caching](http-caching.md).

## Optional frontend tools

Tailwind, Vite and other tools may write their final output into the same static directory. Make
`wogeAssets` depend on that generation task so its inputs are complete before hashing. For a generated
tree outside the source directory, configure `WogeAssetsTask.sourceDirectory` and `sourceFiles`
together to reference that tree; use one merged input tree with the relative paths you want to serve.
The generated output uses a dedicated `woge-assets/resources` directory.

Woge does not transform frontend source or rewrite absolute CSS URLs. An absolute `/images/logo.svg`
still requests the original path; use relative references or let your frontend tool own its URL
rewriting. Development hot CSS and Tailwind updates remain a separate feature under
[#152](https://github.com/christian-draeger/woge/issues/152).

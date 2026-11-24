# Woge documentation build

Run with JDK 21 from the repository root.

```sh
./gradlew -p site/generator check
python3 -m http.server 8080 --directory site/generator/build/site
```

Open [the local documentation](http://localhost:8080/). Use `generateSite` for a build without the renderer tests. Append `--offline` after the Gradle distribution, plugins, and dependencies have been resolved. The generator uses Woge core from the same checkout through a Gradle composite build. It reads local content, source snippets, styles and scripts. It contacts no live app, artifact service, or secret during rendering.

For a GitHub project subpath, generate with `-PsiteBasePath=/woge/` and mount the generated directory at `/woge/` on a static server. Use the default `/` for root hosting, including a custom domain. URLs use stable filenames and heading anchors. The build fingerprints assets by SHA-256 and records the checkout revision and `wogeVersion` from the root Gradle properties. A local build includes working tree edits. Build artifacts intended for publication from clean checkouts and keep the full source revision with the artifact.

## Content

- The five pages in `site/content` contain navigation-level prose and link to the canonical repository guides. Author titles and descriptions in the two front matter fields. Use CommonMark with tables and headings starting at level two. Link to a page with `start.md#run-from-source`. The renderer maps the Markdown target to its static HTML URL.
- Keep examples in their checked-in application or scaffold. Insert reviewed Kotlin source with a standalone `<!-- snippet: examples/path/to/src/Page.kt -->` marker. The build validates the path, then escapes the source when it inserts it. When replacing a heading, update its inbound anchor links.
- `site/static` owns the stylesheet and optional copy-control module. The content remains usable with JavaScript disabled. The output in `site/generator/build/site` is generated.
- Generated API references and [machine-readable agent guidance](../../docs/ai-dx/woge-framework-index.json) follow their existing tooling. Use the [publication guide](../../docs/guides/packages.md) for artifact and channel status. Link the separate [versioned Todo container](../../docs/guides/todo-container.md) and [multi-host example](../../examples/reference-application/README.md) for runnable workflows.

## Rendering and hosting choice

Issue [#301](https://github.com/christian-draeger/woge/issues/301) evaluates how to publish the public docs in [#300](https://github.com/christian-draeger/woge/issues/300).

| Option | Authoring and maintenance | Preview, versions, accessibility, and operations |
| --- | --- | --- |
| Woge at build time | Write Markdown and keep snippets in source. CommonMark parses the documents; Woge renders a small semantic template and escapes the content. Maintain a five-page navigation model, a bounded Markdown renderer and asset links. | Preview on any static server. Pages use the same layout without JavaScript and show the source revision and Woge version. Hosting needs static files only. |
| Live Woge application | Author the same content and wire it through a JVM host. Dynamic endpoints can share request state, which these pages do not need. | Preview requires running the application. Each deployment needs a server and operational ownership; version serving, caching and accessibility remain application responsibilities. |
| [MkDocs](https://www.mkdocs.org/user-guide/deploying-your-docs/) | Markdown, navigation, themes, and static asset handling are built in. An established option for a larger docs tree or a need for theme/plugin features. | Includes local preview and static output. Hosting cost is similar to Woge static output. The chosen theme governs accessibility, and multiple released versions require additional tooling or directory management. |

Use Woge build-time rendering while the scope stays at Markdown, reviewed snippets, and simple navigation. This keeps one small Woge HTML consumer in the repo. The generator is internal and its Markdown dependencies stay outside Woge core. Reconsider an established generator if the site needs search, localization, or a larger versioned documentation tree.

## Publishing candidate

[GitHub Pages custom workflows](https://docs.github.com/en/pages/getting-started-with-github-pages/using-custom-workflows-with-github-pages) can serve the static directory. The pull-request preview job builds at `/woge/`, runs Chromium with JavaScript disabled, and archives `woge-docs-preview` with read-only repository access. Unpack the preview artifact in a `woge` subdirectory and serve its parent directory locally. The preview is an Actions artifact, with no published URL and no deployment credentials.

For an approved release, build the reviewed clean commit with the intended base path. A future deployment workflow would upload the static directory as a Pages artifact and deploy from a protected Pages environment with explicit approval. Keep approved release artifacts and their source revisions available separately from short-lived PR previews. For rollback, deploy the prior approved artifact at its original base path. Rebuild from the recorded clean commit and pinned dependencies if the artifact has expired.

Before enabling deployment, check root and project-subpath links, HTTPS, missing-path responses, HTML and hashed asset cache behavior, and rollback on the selected host. The local renderer adds no Pages workflow or public endpoint. A live JVM demo has its own deployment, resource, and security requirements.

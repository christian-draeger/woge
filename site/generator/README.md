# Woge documentation build

Run with JDK 21 from the repository root.

```sh
./gradlew -p site/generator check
python3 -m http.server 8080 --directory site/generator/build/site
```

Open [the local documentation](http://localhost:8080/). Use `generateSite` for a build without the renderer tests. Append `--offline` after the Gradle distribution, plugins, and dependencies have been resolved. The generator uses Woge core from the same checkout through a Gradle composite build. It reads local content, source snippets, styles and scripts. It contacts no live app, artifact service, or secret during rendering.

For a GitHub project subpath, generate with `-PsiteBasePath=/woge/` and mount the generated directory at `/woge/` on a static server. Use the default `/` for root hosting, including a custom domain. URLs use stable filenames and heading anchors. The build fingerprints assets by SHA-256 and records the checkout revision and `wogeVersion` from the root Gradle properties. A local build includes working tree edits. Build artifacts intended for publication from clean checkouts and keep the full source revision with the artifact.

## Content

- The five pages in `site/content` contain navigation-level prose and link to the canonical repository guides. Author titles and descriptions in the two front matter fields. Use CommonMark with tables and headings starting at level two. Link to a page with `start.md#run-from-source`. The renderer maps the Markdown target to its static HTML URL. Links to Woge's GitHub source on `main` resolve to the build commit. Issue and external links retain their authored URLs.
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

## Preview and publish

[The documentation workflow](../../.github/workflows/docs-preview.yml) builds pull requests at `/woge/`, runs Chromium with JavaScript disabled and archives `woge-docs-preview` with read-only repository access. Unpack a project-path artifact in a `woge` subdirectory and serve its parent directory locally. Serve a root-path artifact directly.

For an approved edition, manually run **Documentation preview** on `main`. Supply the full reviewed commit SHA, the Woge version from that commit and the target base path. Start with **Deploy the checked artifact to GitHub Pages** cleared. Inspect the archived HTML and confirm its version, commit and links. The build verifies that the revision belongs to `main` and that the rendered version matches the requested edition. Later changes on `main` cannot change the selected commit.

For public deployment, configure GitHub Pages to use Actions and restrict the `github-pages` environment to `main` with the repository's deployment approval. Dispatch the same inputs with publishing enabled. Only the deploy job receives Pages and OIDC write permissions. It checks the configured site base path before publishing. Keep the approved revision, version and base path in the release record. To roll back, dispatch the prior approved values. This rebuilds the same static files from the pinned revision and dependencies.

After a deployment, check HTTPS, a documentation deep link, a missing path with the custom 404, and the response headers for HTML and a fingerprinted asset. Repeat after a rollback to confirm the version and asset identity. Keep live JVM demos on their separate deployment paths. [GitHub Pages custom workflows](https://docs.github.com/en/pages/getting-started-with-github-pages/using-custom-workflows-with-github-pages)

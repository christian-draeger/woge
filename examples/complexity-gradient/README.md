# Complexity gradient

Nine small, working Woge solutions, ordered from easy to demanding. Each is what a web developer
would write by following the guides. The ladder shows how much code and how many Woge ideas each kind of
page needs.

| # | Task | What it adds |
| --- | --- | --- |
| 1 | `static-page` | One typed route and a complete HTML document with a stylesheet |
| 2 | `component` | A reusable component as a plain Kotlin function |
| 3 | `form` | A native form, a typed action, redirect and reload |
| 4 | `validation` | Accessible server-side field and summary errors |
| 5 | `enhanced-action` | The same action also updates two regions in the browser |
| 6 | `deferred-region` | A slow region that streams in after the page |
| 7 | `live-update` | Server-sent events that refresh a region |
| 8 | `custom-javascript` | Your own small script on ordinary HTML |
| 9 | `island` | A browser-owned island inside a patched region |

Every task is one package under `src/main/kotlin/dev/woge/examples/gradient/`, with its own Spring
WebFlux `Routes.kt` and its CSS and JavaScript under `src/main/resources/gradient/<task>/`. Tasks do not
import each other. Serve the resources as `/assets/<task>/...` and the Woge fallback client as
`/assets/woge/index.js`. Form actions use the built-in same-origin request context. Results and
interpretation are in
[the complexity gradient report](../../docs/ai-dx/complexity-gradient.md).

## Check it

```sh
./gradlew :woge-complexity-gradient:check
```

This compiles every task and runs a short behavior test for each. It also measures each task (files,
non-blank lines, Woge annotations, imported Woge concepts, hand-written route bindings, and
protocol or host internals) and compares the result with `gradient-baseline.json`. Tasks 1 to 4 must not
need any protocol or host internals.

## Update the baseline

If you change a task on purpose, regenerate the measurements and commit the new file:

```sh
./gradlew :woge-complexity-gradient:test -Pwoge.gradient.update=true
```

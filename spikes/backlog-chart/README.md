# Warehouse backlog chart experiment

An operator needs exact daily packing and dispatch counts and a way to see when the queues diverge.
The server-rendered table and date-range form provide the exact values with JavaScript disabled.
The optional chart overlays two time series and lets the operator focus on the last four days or
hide packing locally. Those actions need immediate visual redraws and retain browser-only state.
The data is synthetic, public, and read-only.

## Run the proof

Use JDK 21, Node and npm, and the [fixed Woge beta pilot repository](https://github.com/christian-draeger/woge/issues/50).
The spike uses Woge `0.1.0-beta.1`, Kotlin 2.4.10, and Ktor Netty 3.5.2.

```sh
npm --prefix spikes/backlog-chart ci --ignore-scripts
./gradlew -p spikes/backlog-chart -PwogeRepository=/absolute/path/to/pilot/repository installDist
npm --prefix spikes/backlog-chart exec -- playwright install chromium
npm --prefix spikes/backlog-chart test
```

Start [the local report](http://127.0.0.1:8080/backlog) with
`spikes/backlog-chart/build/install/woge-backlog-chart-spike/bin/woge-backlog-chart-spike`.
After resolving dependencies, use `npm ci --offline --ignore-scripts` and Gradle `--offline` to
rebuild. Node packages and browser binaries are needed only for this optional spike's build and test.
The installed server serves the chart, its MIT notices, and all Woge assets from the same origin.

## Ownership and lifecycle

- The Kotlin page renders the day rows, statuses, and native date selection. The action validates
  the snapshot and applies Woge's same-origin request policy. A native submit redirects to the
  matching report URL. An enhanced submit replaces the panel and the separate status region.
- The [island module](widget/chart.js) parses public string props, creates one Chart.js instance,
  and owns only the canvas and its two control buttons. It uses no portal, external asset, or
  async data fetch. Tab and native button activation work independently of the canvas. The named
  canvas describes the solid and dashed lines, and the table remains available for exact values.
- Replacement keeps the keyed DOM and local choices. After the server patches its own table, the
  chart reads those public values and calls the existing island manager's explicit update. The
  new values redraw the same canvas with the selected viewport and series visibility.
- [Reset mode](http://127.0.0.1:8080/backlog?reset=1) opts the panel into `data-woge-state="reset"`.
  Woge aborts the owned listeners and the island calls Chart.js `destroy()` before replacement.
  The new instance starts with the default viewport and visible packing series. A failed import
  leaves table navigation and date selection usable, including subsequent server patches.

## Live replacement and browser history

Open [the shared live report](http://127.0.0.1:8080/backlog/live) in two tabs. A native date
selection updates the process-local snapshot choice. Subscribers receive a Woge SSE invalidation
and fetch the current server-rendered panel. This fixture uses the same table and chart with
read-only backlog counts. To see deliberate disposal, open the second tab in
[reset mode](http://127.0.0.1:8080/backlog/live?reset=1).

The live test verifies two real SSE connections and follow-up patch responses. On the keyed tab,
the server table is replaced and the same chart redraws with both local settings intact. The
reset tab detaches the old canvas, restores its pre-mount display state via Chart.js disposal,
and mounts a chart with its default settings.

The [history test](browser/history.spec.mjs) follows ordinary document navigation and lets the
browser choose whether to restore the document. It removes Playwright’s default Chromium switch
that disables the back/forward cache. With Chromium 151.0.7922.34 in full headless mode on macOS
arm64, the browser reported `pageshow.persisted=true`, kept the same chart root and local choice,
and the chart still responded to its controls. The next date selection completed through native
303 navigation. The test also accepts a fresh document with a working chart at its default
settings. Continued SSE delivery on a restored live document remains a separate case.
[Chromium back/forward cache diagnostics](https://developer.chrome.com/docs/devtools/application/back-forward-cache)

## Measured cost and decision

| Asset | Raw bytes | Gzip bytes | Brotli bytes |
| --- | ---: | ---: | ---: |
| Chart.js plus application island bundle | 143,634 | 51,159 | 44,881 |
| Application island source alone | 3,348 | 1,339 | 1,122 |
| Report stylesheet | 672 | 357 | 265 |

Source bytes show the size of the integration code. Bundle sizes include Chart.js 4.6.2 and its
single additional runtime package, `@kurkle/color` 0.6.6. Both are MIT-licensed, with notices copied
into the JVM distribution. The module is tree-shaken to the line controller and its axis and point
components. Files were measured with gzip level 9 and default Node Brotli. The existing Woge
browser assets are outside these counts.

In three loopback Chromium 151.0.7922.34 runs on macOS arm64, the initial report issued zero chart
requests before the chart became visible. Each first module response contained 143,634 bytes and
completed in 4.7–5.5 ms. The automated scroll-to-visible step took 48.2–48.3 ms, keyboard-to-observed
viewport change 5.9–6.1 ms, and the enhanced action response 8.7–10.3 ms. These are observations
from a local test with synthetic data and Playwright assertions. No cross-library performance
comparison was run.

The table suffices for reading exact counts and choosing dates. A small plain module suffices
when the app only needs controls and text feedback, as in the
[notes island](../../docs/guides/local-islands.md). For the backlog overlay, Chart.js supplies axes,
plotting, series visibility, and redraw, at the measured bundle cost and an additional npm build.
Keep it opt-in on reports with a concrete trend-reading need. Keep URL-backed pagination and
ordinary edit links for large record sets until a real editing workflow justifies a virtualized grid.

## Verification limits

The [Chromium tests](browser/chart.spec.mjs) cover native date selection without JavaScript, lazy
loading, keyboard controls, strict CSP, preserved keyed state and canvas redraw after a Woge parent
patch, deliberate disposal and remount, failed import, emulated touch, and invalid and cross-origin
submissions. This example uses read-only data and exercises no mutation retry or authentication policy.

Check physical touch, visible keyboard focus, chart control labels, and the table's reading order
with the [manual review](https://github.com/christian-draeger/woge/issues/253). The automated live
replacement and history checks cover Chromium. Firefox, WebKit and human assistive-technology
behavior need separate verification.

[Chart.js release and license](https://github.com/chartjs/Chart.js/releases/tag/v4.6.2) ·
[Chart.js lifecycle API](https://www.chartjs.org/docs/latest/developers/api.html) ·
[Chart.js canvas accessibility](https://www.chartjs.org/docs/latest/general/accessibility.html) ·
[Widget integration issue](https://github.com/christian-draeger/woge/issues/288)

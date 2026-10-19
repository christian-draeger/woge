# Consumer testing investigation

State: Frozen. Decisions: [ADR 0112](../../docs/adr/0112-consumer-testing-layers.md) and
[ADR 0114](../../docs/adr/0114-webmcp-managed-form-experiment.md). Issues: #267, #268, #274.
This independent experiment is not an application dependency or a public test API.
Time box: one Todo consumer, three journeys, three browser engines, one host.

## Reproduce

Build the [Todo container](../../docs/guides/todo-container.md) as `woge-todo:roadmap` first.
From the Woge root, with JDK 21:

```shell
./gradlew -p spikes/consumer-testing installDist
npm --prefix spikes/consumer-testing ci --ignore-scripts
python3 spikes/consumer-testing/run.py
python3 spikes/consumer-testing/run.py --node
python3 spikes/consumer-testing/run.py --webmcp
```

Each run creates and removes its own app/container. Docker provides native Linux browser libraries;
the JVM/Node images pin Playwright 1.63.0. The DSL source is fetched at the exact MIT-licensed
`9adefaed1c1c6c34f356479aa952a8d22eda238e` commit; its license is downloaded alongside it.
Only its Playwright dependency is modernized for this comparison. Production imports none of it.
The unchanged Node Todo test source is copied into an isolated 1.63.0 test directory.
Application browser CI keeps its existing 1.62.1 pin and separately passed the same journeys.

With a Todo app already listening on loopback:

```shell
WOGE_TODO_URL=http://127.0.0.1:8080 ./gradlew -p spikes/consumer-testing skrapeProbe
```

The HTML-only probe uses published `skrapeit-html-parser:1.3.0-alpha.2` and the JDK HTTP client.
It launches no browser. The Gradle project includes Playwright only because this investigation
compares both layers; a consumer HTML test needs only the parser, its test runner and host transport.

## Recorded evidence, 2026-10-20

Linux arm64 Docker, JDK 21 build, Playwright Java/Node 1.63.0, Chromium 153.0.8010.12.
The application image was built from candidate `0.1.0-SNAPSHOT` artifacts, not a published release.
[Results](results.md) distinguish execution from API inspection and remaining evaluation work.
Traces are generated under `build/evidence`, Node traces/reports under `build/node-results` and
`build/node-report`. These generated files are not shipped with applications.

# Spring Boot reload topology spike

**State:** Frozen. The accepted decision is [ADR 0041](../../docs/adr/0041-orchestrator-owned-spring-reload-and-sse-channel.md).

This spike answers one question: what is the smallest reliable process and browser topology for the
first Spring Boot `wogeDev` loop?

It materializes the canonical Spring Boot scaffold and adds test-only probes. The probes compare a
Spring DevTools trigger-file restart with a full restart of a Woge-managed child JVM. A separate
browser fixture exercises the proposed SSE lifecycle channel with real `EventSource` clients.

The spike is evidence, not a reusable development runtime. Production work follows in:

- [#147](https://github.com/christian-draeger/woge/issues/147) — shared orchestrator;
- [#144](https://github.com/christian-draeger/woge/issues/144) — Spring Boot restart adapter;
- [#145](https://github.com/christian-draeger/woge/issues/145) — browser lifecycle channel.

## Run it

Requirements are JDK 21, Node.js 20 or newer, npm, curl and Python 3. Node is used only by the
Playwright test harness; it is not part of the selected HTML-only application topology.

```bash
npm ci --prefix spikes/spring-boot-reload-topology
npx --prefix spikes/spring-boot-reload-topology playwright install chromium
./spikes/spring-boot-reload-topology/validate.sh
```

For repeated local measurements:

```bash
./spikes/spring-boot-reload-topology/measure-spring.sh 5 build/reload-measurements.tsv
```

The checked-in conclusion and representative measurements are in [evidence.md](evidence.md).

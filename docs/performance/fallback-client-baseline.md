# Fallback client implementation baseline

Recorded on 2026-09-04 with Node.js 26.3.0, esbuild 0.28.2 and Playwright 1.62.1. Release budgets are
set separately in issue #46; these numbers make the first production implementation visible.

## Transfer size

The ES-module bundle contains frame decoding, canonical metadata parsing, 64-bit counter handling,
active-page registry validation, active-content checks, lifecycle events and DOM replacement.

| Artifact | Bytes |
| --- | ---: |
| Minified ES module | 12,730 |
| gzip level 9 | 4,753 |
| Brotli default | 4,144 |

`npm run measure` regenerates `build/size-metrics.json`, prints the values and includes the bundle
SHA-256. The production source and dependencies are deterministic through `package-lock.json`.

## Browser timing smoke measurement

The Playwright contract applies 20 small Replace frames to 20 registered regions and reports the
elapsed `performance.now()` duration. It also reports dynamic module load, parse and evaluation as one
end-to-end browser number; transport and worker scheduling mean it is not a parser-only benchmark.

One local run produced:

| Engine | Module load/parse/evaluate | Apply 20 patches |
| --- | ---: | ---: |
| Chromium | 8.0 ms | 1.4 ms |
| Firefox | 9.0 ms | 2.0 ms |
| WebKit | 3.0 ms | 3.0 ms |

These timings are smoke evidence, not stable performance claims. CI logs fresh values and attaches one
JSON timing artifact per engine. Size is deterministic enough for future regression budgets; runtime
timing needs a controlled benchmark environment before it receives a threshold.

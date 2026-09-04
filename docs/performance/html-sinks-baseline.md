# HTML sink performance baseline

This baseline makes changes to buffering and streaming allocation measurable. It is not a release
budget and should not be compared across machines as though the numbers were absolute.

## Fixture

[`HtmlSinkBenchmark.java`](../../modules/woge-core/src/jmh/java/dev/woge/html/HtmlSinkBenchmark.java)
writes the same pre-encoded three-fragment row to:

- `BufferedHtmlSink`, including the final complete `String` snapshot;
- `StreamingHtmlSink` with an 8-Ki-character bound and a counting downstream sink.

The 32-row case represents a small fragment. The 512-row case forces multiple streamed chunks and
represents a larger region. JMH compiles during `./gradlew check`; execution is explicit:

```shell
./gradlew :woge-core:jmh
```

The build enables JMH's GC profiler and writes machine-readable output to
`modules/woge-core/build/results/jmh/results.json`.

## Initial local result

- Date: 2026-09-04
- Revision: working tree for issue #17 after `d98b0fc`
- Platform: macOS 26.6.1, arm64
- Benchmark JVM: OpenJDK 21.0.6
- JMH: 1.37; one fork; two 250 ms warmups; three 250 ms measurements; one thread

| Sink | Rows | Throughput (operations/second) | Allocation (bytes/operation) |
| --- | ---: | ---: | ---: |
| Buffered | 32 | 2,401,557 | 10,656 |
| Streaming | 32 | 1,453,128 | 10,800 |
| Buffered | 512 | 147,857 | 172,000 |
| Streaming | 512 | 121,607 | 48,304 |

On this short local run, buffering is faster for both sizes. The streaming path reduces normalized
allocation by about 72% for 512 rows because it does not retain and copy a complete final document;
for 32 rows it allocates slightly more. This supports the API split: buffer small fragments and choose
bounded streaming for incrementality or larger output. The confidence interval for the small
streaming case is wide, so these numbers are a starting point, not a regression threshold.

Future comparisons must use the same fixture parameters and record JVM, operating system, hardware,
JMH settings and revision. A release performance budget belongs to the end-to-end adapter/proxy
benchmarks after the walking skeleton exists.

# Controlled Todo development feedback trial

Two fresh autonomous Codex sessions each repaired an incomplete
[TDX-02](../todo-corpus-v0.1.md) rename on the same fixed Woge beta consumer.
The control read the running development loop's terminal. The other session
used the actual local MCP endpoint through the
[repository CLI](../../../scripts/woge-mcp.mjs), with a 2025-06-18 protocol offer.
The outcome informs [#273](https://github.com/christian-draeger/woge/issues/273).

## Setup

- Fixed Spring Boot WebFlux Todo pilot, Woge `0.1.0-beta.1`, framework and
  public guidance at `7bf1e3e53aa8f4332828eec9231882aae1aee275`. Both app
  copies had Git tree `4a64d1b670c05b7ce03191098aef0c5f84d56633`.
- macOS arm64, JDK 21.0.6, Gradle 8.14.4, Kotlin 2.4.10, Spring Boot 4.1.1
  and warmed dependency caches. Chromium 151.0.7922.34 with Playwright 1.62.1.
- The sessions inherited the same Codex model configuration. The exact
  deployed model identifier was unavailable to the evaluator. Each started
  with a fresh conversation and received the same public task, consumer, and
  pinned guidance. Participants were told their own feedback channel and never
  shown the other participant's output.
- Separate loopback `wogeDev` processes ran sequentially, terminal first.
  Both initially served build #1. The control ran without `--mcp` and created
  no MCP connection file. The MCP arm had no terminal log in its task materials.

## Task and independent checks

The participant started from a ready build and applied the same deterministic
seed before inspecting app source. The seed renamed only the Kotlin declaration
and preserved the serialized field name.

```diff
 data class AddTodo(
-    val title: String,
+    @kotlinx.serialization.SerialName("title")
+    val text: String,
```

The untouched references intentionally broke compilation. The task required
a source-located diagnostic from the assigned feedback channel, a repair without
changing the wire field or its error association, and new build readiness.
Both seeds used the same script and starting file. The control read terminal
build markers. The MCP participant used the documented build cursor and
`await_build`. A successful HTTP response from the old build did not count
as recovery.

The evaluator ran unchanged tests against the clean app and both final app
copies. The JVM and HTTP checks exercise the ordinary redirects and form field,
validation association, origin policy, and actual enhanced patch codec.
The browser check uses Chromium in native and enhanced modes. The executable
JAR also passed the production artifact exclusion check.

```sh
./gradlew --offline --console=plain check bootJar verifyWogeProductionArtifact test --rerun
npm run test:browser -- --project chromium-native --project chromium-enhanced
```

## Observations

Both channels exposed build #2 failing in
`src/main/kotlin/example/todo/TodoActions.kt`. At 33:26 the compiler could
not infer the field value type. At 33:41, 43:25 and 47:71 it reported
unresolved `title` references. Both participants made one repair to the same
application file. They used the renamed property with explicit serialized
field metadata while retaining the old form field and error association.
Their resulting source files were byte-identical.

| Observation | Terminal | MCP |
| --- | ---: | ---: |
| Seed to first correct diagnostic observation | 7.10 s | 9.72 s |
| Diagnostic observation to recorded repair | 14.73 s | 12.23 s |
| Recorded repair to newly ready build observation | 12.16 s | 8.35 s |
| Participant start to new readiness observation | 34.02 s | 30.34 s |
| Seeded failed builds | 1 | 1 |
| Repair attempts | 1 | 1 |
| Failed repair builds, unsupported API attempts or stale-build actions | 0 | 0 |

The terminal observed build #3 succeed and then a fresh ready marker after
the application restarted. MCP reported build #3 successful, `READY` and
serving generation #2 with zero diagnostics. The MCP participant's initial
sandboxed localhost connection was denied and succeeded through normal
approval before the timed start. Neither participant received corrective
help. Both application listeners stopped and the MCP connection file was
removed on shutdown.

Both final apps passed six JVM/HTTP checks, eight Chromium journeys, four
expected native-mode skips, and the production artifact check.

## Interpretation and decision

The terminal made the source location and new readiness visible in a single
log. The MCP endpoint provided the same diagnostic with a structured build
number and a serving generation, without opening the terminal log.
Neither participant tried to act on a stale build. This trial establishes a
usable development workflow for both routes.

The marks include participant and tool overhead. They come from one seeded
failure, one session per channel and a fixed terminal-first order. The two
runs shared warm caches, so the second run could reuse outputs from the
byte-identical repair. MCP also requests a manifest during its build.
The observed times cannot establish a speed advantage. Other model families
and unaided human participants were outside this trial.

Retain the existing experimental tools and CLI fallback under
[ADR 0113](../../adr/0113-retain-experimental-development-mcp.md).

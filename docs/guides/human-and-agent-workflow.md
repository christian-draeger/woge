# Develop Woge applications with humans and coding agents

Woge exposes ordinary HTML, HTTP, links and forms through Kotlin. The same public APIs, examples
and verification commands are available to people and coding agents. Start with the
[standalone Todo application](../../examples/todo-spring-boot/README.md), or the
[versioned scaffold](../../scaffolds/spring-boot/README.md).

## Start with the actual consumer version

The Todo application pins its published dependencies in gradle.properties and includes
[application guidance](../../examples/todo-spring-boot/AGENTS.md). Its test gate checks that the
checked-in guidance and version pins agree. Framework development may test explicit candidate-version
overrides; that does not imply an older published artifact contains a newly documented API.

Use the [package setup](packages.md), including GitHub Packages read permission. Keep credentials in
the supported environment variables. A dependency/plugin-resolution failure is not evidence that an
application needs a different Woge API.

Read the complete source for a working page/form before adapting it. Kotlin details appear in the
[Kotlin bridge](kotlin-for-web-developers.md). Standard labels, inputs, names, URLs and CSS stay visible.

## Use one feedback loop

From the consumer directory:

```shell
./gradlew wogeTasks --format=json
./gradlew wogeDev
./gradlew check bootJar
```

A coding agent can use the same loop with experimental `wogeDev --mcp`. The local integration offers
status, await_build, get_diagnostics, reload, restart, get_manifest and get_dev_urls.
Remember the build cursor before editing, wait for the resulting build/ready state, then inspect the
page. Never mistake the last valid running application for a newly compiled version after a failure.
See [development MCP](development-mcp.md) for supported setup and security boundaries.

The [manifest](application-manifest.md) lists structural descriptors without loading application
objects. It is an aid to discovery, not a runtime permission registry or a browser substitute.

## Verify at the useful layer

| Question | First useful check |
| --- | --- |
| Did a typed route/action/field/region reference drift? | Kotlin compilation and generated diagnostics |
| Does the repository mutate/filter correctly? | Direct application tests, including concurrency where shared |
| Do native forms, authorization and redirects work? | Real HTTP tests with redirects observed explicitly |
| Is submitted text escaped and validation associated with controls? | Parsed HTML assertions |
| Did the enhanced result carry the expected updates? | The real Woge patch codec and result contract |
| Are focus, DOM preservation, CSS and no-JavaScript flows usable? | Targeted real-browser journeys |
| Does the deployable artifact start/stream/stop? | Production JAR/container and proxy smoke |

Run the same checks for human- and agent-authored changes. Successful compilation does not establish
domain correctness, authorization, accessibility or a complete browser journey.
The Todo browser tests exercise three engines with native and enhanced projects; manual assistive-
technology and usability evidence still needs people.

## Recover from mistakes without adding another framework

Use the code/file/line in compiler diagnostics. Confirm imports and the installed Woge version before
guessing names. Keep generated sources generated. If a public API is missing or a guide is stale,
record a small reproducible finding. Dynamic data belongs in escaped text and typed URLs, not raw HTML.
Native/enhanced handlers share one policy and action; an uncertain POST is never retried automatically.

The [data freshness guide](data-freshness.md) explains state ownership and recovery. Complex islands
remain optional. Plain HTML/CSS projects do not require a Node/Vite/React-style application stack.

## Current evidence and limits

The compiler corpus, adapter TCK, browser gates, production-artifact checks and
[complexity gradient](../ai-dx/complexity-gradient.md) are deterministic evidence.
Existing [AI-DX results](../ai-dx/results/README.md) cover stated historical versions and tasks; they
do not establish a universal productivity or "best framework" claim.

Use the [Todo evaluation corpus](../ai-dx/todo-corpus-v0.1.md) for a current comparison. #181 owns the
human control, #49 owns the quickstart usability trial, #253 owns themed/manual verification and
#50 owns independent design-partner feedback. Record incomplete or failed runs as such.

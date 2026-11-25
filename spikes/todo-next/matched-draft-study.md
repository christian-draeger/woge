# Matched Todo deployment study with a pending draft

## Protocol fixed before execution

This is a new run for [#277](https://github.com/christian-draeger/woge/issues/277). Keep the [original two-contender result](README.md#parity-decision) and its archive. Use the existing Next.js and KTE/htmx implementations and the maintained Spring WebFlux Woge Todo. Stage the ten identical initial titles in a disposable Woge packaging copy. Change only its in-memory bootstrap; the maintained source and server, form and client behavior remain the same.

A contender must pass the same useful initial HTML, escaped titles, no-JavaScript filters, rejected 400 with the literal input, successful native 303, and enhanced rejected 400 with error focus. The enhanced Add must render the same escaped row, count and status. Before the POST reaches the server, use the same one-shot browser interception for each contender and enter a newer draft in the submitting tab. Release before sampling. Verify that both the submitting tab and a separate tab preserve their respective drafts, selections and focus while the second tab receives the new row. Keep the foreign-origin denial. An invalid scenario or broken observable result stays in the record and is excluded from timing claims. The behavior of the submitted field without a newer draft differs and remains outside this common timed task.

Use the same pinned `linux/arm64` runtime images as the initial study, two CPUs and 768 MiB per application, fresh processes, and a single local Chromium build with new contexts. Rotate all passing contenders across three rounds. Per process measure time to the first useful response containing all ten seeded rows, browser-visible outcome and resource bytes, post-browser process RSS, and one persistent closed-loop GET client with 15 warmups and 60 measured responses. Retain all successes and failures. Neither loopback latency nor timing within a browser proves production capacity or framework-wide performance.

Measure dependency setup and clean builds separately in the Linux images. Keep the installed Node and JDK/Gradle toolchain outside the cold-dependency timer. For each contender take one first dependency setup from its own empty persistent cache, and three rotated clean output builds from its own warmed dependencies, without Gradle task-output or Next.js build-output caches. Keep dependency resolution and offline build logs separate. Record exact commands and sample boundaries. Woge's source-matched local Maven artifacts are prepared in a separate source build before the consumer rounds. If a fresh cold cache cannot complete, keep its failure and any successful warm-dependency evidence under the actual conditions.

## Fixture and scope

This fixture revision is `todo-deployment-pending-draft-v1` under the [comparison contract](../../docs/performance/comparison-contract.md). It covers the initial board and filters, the Add subset of the native and enhanced tasks, validation and the connected two-tab live case. The ten seeded titles are active. All three run the browser test against a new process with the same CSS and the same ordered starting data. Within each process the native and enhanced add each create one row. A rejected validation or foreign-origin submission leaves the counts unchanged.

| Application | Hosting and presentation |
| --- | --- |
| Next.js | Standalone Node server, server-rendered React and native validation HTML, application JSON updates and SSE invalidation |
| KTE/htmx | Spring Boot WebFlux and Reactor Netty, compiled KTE templates, htmx HTML updates and SSE invalidation |
| Woge | Spring Boot WebFlux and Reactor Netty, Kotlin HTML builders, Woge patch stream and SSE invalidation/region reload |

The distributions include the shipped application features and dependencies. Woge renders Toggle and Delete controls and includes Actuator. The Next.js and KTE fixtures cover Add. The served HTML and package footprints include those differences. This is a deployment-level comparison of the scoped task. Authenticated policies, idempotency, uncertain-write recovery, Toggle/Delete, deferred regions and reconnect behavior have no equivalent three-way result here. The no-new-draft case and Woge exclusion remain in the [original parity report](README.md#parity-decision).

## Reproduce

Use a disposable checkout and Python 3.12 or newer. Set `study_gradle` to a verified, unpacked Gradle 8.14.4 binary distribution. The [Gradle caching](https://docs.gradle.org/current/userguide/dependency_caching.html) and [Next.js standalone output](https://nextjs.org/docs/pages/api-reference/config/next-config-js/output) documentation describe the cache and package model. The browser uses the project lockfile and Chromium. [Playwright interception](https://playwright.dev/docs/api/class-page#page-route).

```sh
study_src=7382a75fa026b5445fef238a0f9427186672f6c7
study_dir=$(mktemp -d)
study_gradle=/path/to/gradle-8.14.4

npm --prefix client/woge-fallback-client ci --ignore-scripts
npm --prefix client/woge-fallback-client exec -- playwright install chromium
git worktree add --detach "$study_dir/woge-source" "$study_src"
( cd "$study_dir/woge-source" && ./gradlew publishScaffoldArtifacts -PwogeVersion=0.1.0-SNAPSHOT )
python3 spikes/todo-next/stage-woge.py "$study_dir/woge" --source "$study_src"

docker pull --platform linux/arm64 node@sha256:873f586f3794f52281bd039bf02c048a0be08fde619338f9941bad1eb6b34530
docker pull --platform linux/arm64 eclipse-temurin@sha256:9919f7eb065001785f3c6491886b871385d9b9039f1c0efa0d0bb2c57b6e1b62
docker pull --platform linux/arm64 eclipse-temurin@sha256:4a541e56af0ae9a1ff20cdbf43e400864542116b0e7804b37f51be4c574f2057

python3 spikes/todo-next/build-study.py \
  --woge-stage "$study_dir/woge" \
  --maven-repository "$study_dir/woge-source/build/scaffold-maven-repository" \
  --gradle "$study_gradle" --caches "$study_dir/caches" \
  --output "$study_dir/builds"

python3 spikes/todo-next/study.py --matched-draft \
  --next spikes/todo-next/.next/standalone \
  --kte spikes/todo-kte-htmx/build/libs/todo-kte-htmx.jar \
  --woge "$study_dir/woge/examples/todo-spring-boot/build/libs/woge-todo-0.1.0.jar" \
  --output "$study_dir/runtime"
```

The build runner logs complete argument arrays. `npm ci` installs from an empty npm cache. The Gradle setup task resolves named runtime, build and compiler dependencies from two separate empty Gradle user and project caches. It also resolves implementation JARs used by the pinned KSP execution and the development-only configurations Spring Boot inspects to exclude dependencies from the package. The following offline `clean bootJar` rounds check that the captured dependencies suffice. Cold setup measures each ecosystem's normal setup work, including Gradle configuration or npm unpacking. Local Woge framework publication, preinstalled packages or tools, browser installation and container image pulls sit outside these timings.

All clean builds use two CPUs, 3 GiB, no network, and fresh outputs. Gradle task and configuration caches are disabled and each invocation uses a new single-use daemon. Next.js retains installed dependencies and removes `.next` before rebuilding. Application processes use their separate deployment limits. Startup uses a 50 ms polling interval and stops at the first full initial board. No OS page-cache reset, heap profile, artificial network latency, tracing collector or competing benchmark load is added by the runner. The browser times start when the held request is released. The Playwright route affects browser caching during the interaction. All asset observations are captured before routing, from a fresh browser context with default response compression.

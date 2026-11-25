# Next.js and JVM Todo deployment study

This [#277 study](https://github.com/christian-draeger/woge/issues/277) uses the ten-row dataset, title rules and CSS from the [frozen #269 Todo comparison](../todo-framework-comparison/README.md). The independent distributions are Next.js Pages Router 16.4.6 with React 19.3.1 and Node 24.22.3 LTS, and the [standalone KTE/htmx WebFlux baseline from #341](https://github.com/christian-draeger/woge/pull/341). An unchanged standalone Woge WebFlux Todo was also presented to the parity gate. The deployment uses standalone output, server-side rendering and React’s static HTML renderer for native validation. [Next.js standalone output](https://nextjs.org/docs/pages/api-reference/config/next-config-js/output) · [Pages Router SSR](https://nextjs.org/docs/pages/building-your-application/data-fetching/get-server-side-props) · [React static HTML](https://react.dev/reference/react-dom/server/renderToStaticMarkup).

The [follow-up deployment study](matched-draft-study.md) includes all three applications in a separate run with a new draft typed before the Add reaches the server. Its parity gate and build/cache evidence apply to that scoped task.

## Reproduce

With Node 24.22.3, npm 11.19.1 and JDK 21, use a disposable checkout containing this study and the maintained Todo CSS. The host browser probe uses the repository’s existing Playwright dependencies.

```sh
npm --prefix client/woge-fallback-client ci --ignore-scripts
npm --prefix client/woge-fallback-client exec -- playwright install chromium

docker pull --platform linux/arm64 node@sha256:873f586f3794f52281bd039bf02c048a0be08fde619338f9941bad1eb6b34530
docker pull --platform linux/arm64 eclipse-temurin@sha256:4a541e56af0ae9a1ff20cdbf43e400864542116b0e7804b37f51be4c574f2057
docker run --rm --platform linux/arm64 --cpus 2 --memory 2g \
  --mount "type=bind,source=$PWD,target=/workspace" -w /workspace/spikes/todo-next \
  -e NEXT_TELEMETRY_DISABLED=1 \
  node@sha256:873f586f3794f52281bd039bf02c048a0be08fde619338f9941bad1eb6b34530 \
  sh -ec 'npm ci --ignore-scripts --no-audit --no-fund && npm run build'
```

Build the [KTE executable archive](https://github.com/christian-draeger/woge/tree/fbfd2c8fb1baa6508025656927661b7be12c0588/spikes/todo-kte-htmx) with `./gradlew -p spikes/todo-kte-htmx clean bootJar` and pass its path. To repeat the optional Woge preflight, build the maintained Todo against source-matched local artifacts with `./gradlew publishScaffoldArtifacts -PwogeVersion=0.1.0-SNAPSHOT`, then `./gradlew -p examples/todo-spring-boot bootJar -PwogeRepository="$PWD/build/scaffold-maven-repository" -PwogeVersion=0.1.0-SNAPSHOT`.

```sh
python3 spikes/todo-next/study.py \
  --next spikes/todo-next/.next/standalone \
  --kte /path/to/todo-kte-htmx.jar \
  --woge examples/todo-spring-boot/build/libs/woge-todo-0.1.0.jar \
  --output build/todo-next-study
```

The runner mounts each independently built artifact read-only in its pinned Linux arm64 runtime image. Each new process receives two CPUs, 768 MiB and a random published loopback port. The closed-loop probe uses one persistent connection with 15 warmups and 60 measured `GET /` responses per round. Each response must contain the expected ten-row count and the escaped first row. Three rounds rotate contender order. The browser then verifies the native and enhanced interactions against that same process. Startup measures Docker launch through the first useful HTTP response with 50 ms polling. RSS is the application process’s Linux `VmRSS` after the browser checks. Fresh enhanced Chromium contexts report resource bodies and transfer sizes with runtime-default encoding.

## Parity decision

The scoped gate covers the initial HTML, native filtering, a native 400 preserving invalid text, a native 303 and visible escaped Todo, and an enhanced add that updates the list, count and status and clears the completed input. It also checks enhanced validation focus, two-tab SSE preserving draft/selection/focus and a foreign-origin rejection. The Next.js and KTE applications passed the gate.

Woge passed the native, enhanced validation, list/count/status and peer draft checks in a separate diagnostic. After a successful enhanced add, the submitted title remained the browser’s input value even though the new server default was empty. The field has the stable `todo-title` key and no explicit state-reset marker. This matches [Woge’s browser-owned state policy](../../docs/guides/browser-owned-state.md). The shipped Todo’s browser tests require the row, status and peer draft outcomes and leave clear-on-success unspecified. This study additionally requires a cleared field and excludes Woge from its timed rounds. Native Toggle/Delete, deferred content, resource authorization and independent implementation review remain separate work.

## Observations

The run used macOS arm64 with local OrbStack Docker 29.8.2 and host Chromium 151.0.7922.34. Gradle 8.14.4 and Temurin 21.0.6 built the JVM artifacts. The Linux JVM runtime was Temurin 21.0.13+9. The [raw evidence archive](evidence/2026-11-25-next-kte.tar.gz) contains all 360 measured HTML responses, 90 warmups, six measured browser journeys, parity reports, build attempts and source identities.

| Observation | Next.js | KTE/htmx |
| --- | ---: | ---: |
| Application artifact, bytes | 39,198,618 | 39,164,455 |
| Startup to first useful response, median | 319 ms | 1,310 ms |
| Post-browser process RSS, median | 115.0 MiB | 175.4 MiB |
| Initial HTML GET p95 across rounds | 1.346–1.448 ms | 1.225–1.400 ms |
| Initial scripts, browser-transferred bytes | 120,609 | 55,913 |
| Initial scripts, decoded bytes | 378,814 | 55,313 |
| Enhanced add to all in-tab outcomes, median | 25.65 ms | 26.15 ms |
| Add to peer row visibility, median | 28.55 ms | 29.19 ms |

Artifact bytes count the files in Next’s standalone directory and the packaged JVM JAR, excluding the runtime images. In this fixture Next started sooner and used less process RSS, while KTE transferred less script data. The GET p95 ranges overlap and each browser result is one journey per round. These local samples describe trade-offs for the selected deployment layout and behavior. Production capacity and statistical timing differences require separate evidence. The inspected Woge archive was 43,658,411 bytes and remains an artifact observation.

The Linux Node setup took 5.43 s with an empty npm cache. Three clean-output Linux builds with installed dependencies took 2.98, 2.76 and 2.58 s. The archive separately records three clean-output host build pairs with warm dependencies. Next took 1.73, 1.40 and 1.40 s. KTE took 8.55, 1.18 and 1.09 s. The host sampler retains a preliminary Woge build failure where the default Gradle daemon exhausted Metaspace during KSP and the build reached its 60 s deadline. Comparable cold Gradle setup remains unmeasured. Rerun parity before additional timings, and keep any broader protocol or saturation study separate from this dataset and #269.

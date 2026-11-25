# Todo change safety across five application approaches

Measured on **2026-11-25** for [#291](https://github.com/christian-draeger/woge/issues/291).
The [declared protocol](protocol.md) fixes ordinary incomplete route, form and render edits and
independent observable outcomes. The [raw evidence](evidence/2026-11-25-todo-changes.tar.gz)
includes complete Gradle and browser logs, source locations, generated Hilla output, check flags,
corrected controls and failures from preliminary runs.

## Standalone Todo applications

The three applications come from the [packaged JVM comparison](../todo-datastar/comparison-2026-11-25.md)
at source [`cabaea870919fc7a1dfca0e3664643982ade2207`](https://github.com/christian-draeger/woge/tree/cabaea870919fc7a1dfca0e3664643982ade2207).
The study used the fixed Woge `0.1.0-beta.1` kit at `7bf1e3e`, KTE 3.2.5 with htmx 2.0.11,
and Thymeleaf 3.1.5.RELEASE with Datastar v1.0.5. All three use Spring Boot 4.1.1 WebFlux.
Gradle 8.14.4, Kotlin 2.4.10, BellSoft JDK 21.0.6, Node 24.13.1, Playwright 1.62.1 and Chromium
151.0.7922.34 ran on macOS arm64. The fixture stages the same ten seeded titles in each isolated
package. The [application edits](cases.py) record the exact replacement and expected occurrence
count. The [browser oracle](verify.mjs) inspects user-visible rows, HTTP responses and focus.

| Incomplete edit | Woge | KTE/htmx | Thymeleaf/Datastar | What breaks if the replacement runs |
| --- | --- | --- | --- | --- |
| Route parameter rename | Kotlin | Native | Native | Completed shows active rows. |
| Field rename | Kotlin | Native | Native | The rendered form and handler disagree on the title. |
| Required field addition | Native | Native | Native | Existing valid form submission receives 400. |
| Existing field removal | Kotlin | Native | Native | The Add form loses its required title binding. |
| Field type change | Kotlin | Generated KTE Kotlin | Native | A normal text title no longer matches the input contract. |
| Action endpoint drift | Native | Native | Native | The rendered action hits an unregistered route. |
| Region target rename/drift | Kotlin | Enhanced | Enhanced | The Add POST succeeds without showing the new row in the same document. |
| Repeated public row keys | Native | Native | Native | Distinct rows share an exposed identity. |
| Optional filter without a value | Kotlin | Native | Native | The initial page no longer handles the missing query. |
| Value in the wrong output context | Kotlin | Native | Native | The expected text title is missing. |
| Invalid remote title | Native | Native | Native | Blank input returns 500 instead of a validation response. |
| Stale remote filter choice | Native | Native | Native | The stale `archived` selection no longer yields HTTP 400. |
| Unhandled validation outcome | Enhanced | Enhanced | Enhanced | The rejected action fails to present its error summary correctly or to focus it. |

Each cell is the first observed detector. **Kotlin** and **Generated KTE Kotlin** stop the
build even with the optional browser checks disabled. The generated KTE diagnostic refers to
emitted Kotlin and needs a lookup back to the `@param` declaration. **Native** and **Enhanced**
build successfully and require their respective optional browser assertions. The typed browser
form checks describe generated code separately below. The modified source locations,
raw diagnostics and browser messages appear with each replay result.

The 45 app/case combinations include six unchanged and aligned private-rename controls. All six
passed their intended checks, with no observed false positives among those controls. The fixed-beta Woge build catches seven incomplete edits before the browser. KTE
catches one. The remaining 31 fail an independent browser assertion. These are properties of the
selected source edits and configuration. Interpret the results per edit and configuration.

Three details guide follow-up work. Woge's typed `AddTodo::title` and generated region references
make certain incomplete renames visible at build time. A new required field still compiles with
an older authored form, and a manually chosen valid error-summary ID can disagree with the
form's target. Keeping typed handles for user-facing IDs and testing rendered forms are useful
checks at this boundary. The [compatibility intake](https://github.com/christian-draeger/woge/issues/62)
is the place to evaluate any change to the API or to its generated diagnostics.

| Repeated handwritten names in the measured Add/filter code | Woge | KTE/htmx | Thymeleaf/Datastar |
| --- | ---: | ---: | ---: |
| Exact quoted `title` | 0 | 5 | 3 |
| Exact quoted `filter` | 3 | 6 | 4 |
| Full Add POST path | 0 | 3 | 3 |

These counts cover Woge Todo page, routes and actions, KTE's Todo handler, Add/filter templates
and `todo-htmx.js`, and the Datastar Todo handler and HTML templates. The scan is a literal tally
and counts neither domain property references nor generated files. Woge still declares the
`add-todo` action ID once. The generated `AddTodoAction`, route and region API are part of the
build contract. KTE types the template parameters, while the handler uses string-keyed model
maps. Datastar's Thymeleaf expressions and morph targets remain authored names. This comparison
introduced no casts or raw markup bypasses. The manually hardcoded Woge route variant shows a
place where callers can leave the generated binding.

## The same field type change in Next.js and Hilla

The Next.js 16.4.6 [Pages Router form boundary](next/form.ts) treats request data as unknown and
validates it before returning a typed value. The Hilla 25.3.6 [probe](hilla/pom.xml) calls the
real `@BrowserCallable` generator and a React form imports the emitted service. Both use a
separate, locked TypeScript 6.0.2 check. [Next.js API routes](https://nextjs.org/docs/pages/building-your-application/routing/api-routes)
· [Hilla generated bindings](https://vaadin.com/docs/latest/hilla/reference/typescript-client)

| Probe | With semantic type checking | With only type stripping or transpilation | Coordinated change |
| --- | --- | --- | --- |
| Next.js | Changing the expected field to `number` leaves a `string` from the old parser. `form.ts:10:14` reports TS2322. | Node runs the original string parser and handler despite the changed type declaration. | The numeric parser passes type checking and its independent runtime assertions. |
| Hilla | Java `String` to `Integer` changes the generated field from `title?: string` to `title?: number`. The unchanged caller reports TS2322 at `@index.tsx:7:29`, both in the Vaadin build and with the locked compiler. | TypeScript emits JavaScript containing the stale string call. | The generated contract and numeric React caller compile together. |

The Next.js exercise stops at form parsing and the response contract. The Hilla exercise stops
at Java generation and TypeScript checking. They support comparing the detection stage of one
field-type mismatch. Neither probe establishes server persistence, schema evolution over deployed
client versions or runtime trust in browser values.

## Boundaries and reproduction

An ordinary stale filter value exercises the server allowlist. Protocol ordering is a separate
layer. Woge's [target registry tests](../../client/woge-fallback-client/tests/targets.test.mjs)
and [signals tests](../../client/woge-fallback-client/tests/signals.test.mjs) passed 28 unit checks
with the recovery suite, including older revisions and late outcomes. KTE cancellation and
Datastar's refresh coordination are described in their [separate study](../todo-datastar/comparison-2026-11-25.md).
Equivalent stale-patch delivery, database write conflicts, CSRF and record authorization are
outside this change corpus. Shared types and observed UI state address different behavior.
The reused Woge M1 compiler/corpus check and all 46 existing KSP tests also passed. The optional
invoice browser test was outside that invocation.

Use JDK 21, Node 24, Maven 3.9.16 for Hilla and Python 3.12 or newer. Use the fixed Maven pilot
repository from [#50](https://github.com/christian-draeger/woge/issues/50). Populate the Gradle
dependency cache using the standalone [JVM instructions](../todo-datastar/comparison-2026-11-25.md#reproduce-and-retain)
before the offline variant replay. The reports go to new directories. `--app` and `--case`
repeat to select a smaller group. The optional `--woge-version` points at another explicit
artifact for a separate result. The archive preserves the source-snapshot run as that separate
result.

```sh
study_dir=$(mktemp -d)
pilot_repository=/path/to/fixed-beta/repository
npm --prefix client/woge-fallback-client ci --ignore-scripts
npm --prefix client/woge-fallback-client exec -- playwright install chromium
python3 spikes/todo-change-safety/run.py \
  --repository "$pilot_repository" --output "$study_dir/todo-variants"
python3 spikes/todo-change-safety/typed-probes.py "$study_dir/type-variants"
node --test client/woge-fallback-client/tests/{targets,signals,recovery}.test.mjs
./gradlew :woge-ksp:test :woge-m1-api-corpus:check --offline
```

The typed runner uses real Maven generation and `npm ci`. It resolves its output path before
running Maven to avoid a Vaadin/Vite `tsconfig.json` lookup problem reproduced with the macOS
`/tmp` symlink and an absolute Maven `-f` argument. That initial failure is retained. A change
to the application source, generated API, compiler, browser, optional checks or input contracts
requires a fresh replay for claims about the changed configuration.

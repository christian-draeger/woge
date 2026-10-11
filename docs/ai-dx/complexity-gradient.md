# Complexity gradient

How much code and how many Woge ideas does each kind of page need? The
[complexity gradient](../../examples/complexity-gradient/README.md) answers this with nine small,
working tasks, ordered from a static page to a browser-owned island. Each task is written the way the
public guides teach it, on Spring WebFlux.

The suite is deterministic. `./gradlew :woge-complexity-gradient:check` compiles every task, runs a
behavior test per task and compares the measurements with `gradient-baseline.json`. A change in any
number fails the build until the baseline is regenerated on purpose. No model runs in CI; live AI runs
follow the [evaluation protocol](evaluation.md) and record the same numbers in a
[result record](results/README.md).

## What is measured

| Column | Meaning |
| --- | --- |
| Files | Kotlin, CSS and JavaScript files of the task |
| Lines | Non-blank, non-comment lines |
| Declarations | `@WogeRoute`, `@WogeAction`, `@WogeRegion`, … annotations |
| Concepts | Distinct `dev.woge.*` names the task imports |
| Bindings | Hand-written `handlers.` calls that connect the task to Spring |
| JS | Application JavaScript lines |
| Internals | Protocol or host internals the task imports (should be empty) |

Shared setup that several tasks reuse is listed separately. Its internals are reported as known gaps,
so the cost does not disappear from the numbers.

## Results (suite version 1)

| Task | Files | Lines | Declarations | Concepts | Bindings | JS | Internals |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| static page | 3 | 68 | 1 | 17 | 1 | 0 | none |
| component | 3 | 88 | 1 | 20 | 1 | 0 | none |
| form | 3 | 115 | 2 | 27 | 2 | 0 | none, shared POST context ¹ |
| validation | 3 | 171 | 2 | 40 | 2 | 0 | none, shared POST context ¹ |
| enhanced action | 4 | 189 | 4 | 37 | 2 | 2 | `PageEpoch`, `PageIdentity`, `RenderIdentitySecret`, `TargetRevision`, shared POST context ¹ |
| deferred region | 4 | 134 | 3 | 33 | 2 | 9 | `PageEpoch`, `PageIdentity`, `RenderIdentitySecret`, `patchHtml` |
| live update | 4 | 174 | 4 | 31 | 3 | 25 | `InteractionSequence`, `PageEpoch`, `PageIdentity`, `RenderIdentitySecret`, `TargetRevision` |
| custom JavaScript | 4 | 103 | 1 | 22 | 1 | 8 | none |
| island | 4 | 109 | 2 | 24 | 1 | 18 | `PageEpoch`, `PageIdentity`, `RenderIdentitySecret` |

¹ POST requests need a hand-written request context that uses `RequestTrace`, `RequestId`,
`CorrelationId` and `RequestSecurity`. Tracked in
[#213](https://github.com/christian-draeger/woge/issues/213).

## Comparison with hand-written HTML

The hand-written baseline is the retired
[Spring HTML + htmx spike](https://github.com/christian-draeger/woge/tree/8f9f92e22fab2ecaa1e7a205466949f2bea11a1b/spikes/spring-html-htmx-baseline):
Thymeleaf templates, htmx and plain Spring WebFlux controllers. It implements the same reference journey
as the [Woge reference application](../../examples/reference-application/README.md): deferred regions,
a native form with validation and an enhanced multi-region update. Both are counted the same way
(non-blank, non-comment lines; tests excluded; WebFlux host).

| Journey | Files | Lines | Of which markup/templates | Of which CSS | Of which JS |
| --- | ---: | ---: | ---: | ---: | ---: |
| Hand-written Spring + htmx | 12 | 596 | 194 | 80 | 0 (htmx from a CDN) |
| Woge reference application | 10 | 1083 | in Kotlin | 123 | 67 |

The Woge application does a bit more: it adds live "new task" notices over server-sent events and a
dialog, which the baseline does not have. Even allowing for that, Woge does not save lines yet; it
currently needs more. What it buys today is checking: routes, actions,
form fields, regions and HTML are typed, so a renamed field or region fails to compile instead of
breaking at runtime, and native and enhanced paths share one action. The extra lines come mostly from
the identity and revision plumbing measured in tasks 5 to 7 and from explicit host wiring. Closing that
gap is the goal of the follow-up issues below.

## What the numbers say

- Static pages, components, forms and validation need no protocol or dev-runtime types. Their only gap
  is the POST request context.
- Native and enhanced actions share one action and one outcome model; the enhanced task adds regions,
  not a second handler.
- Enhanced pages leak protocol plumbing. This is the biggest source of extra concepts.

## Follow-up

- [#213](https://github.com/christian-draeger/woge/issues/213) Built-in same-origin request context for POST actions
- [#214](https://github.com/christian-draeger/woge/issues/214) Optional action context, `FormValues` for business-rule errors, clearer duplicate-ID errors
- [#215](https://github.com/christian-draeger/woge/issues/215) Hide identity, epoch and revision plumbing; simpler live regions
- [#216](https://github.com/christian-draeger/woge/issues/216) No-JavaScript fallback for deferred regions

When one of these lands, update the task, regenerate the baseline and update this table.

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
so the cost does not disappear from the numbers. Form actions use the built-in same-origin request
context, so no shared host-specific POST factory contributes to these tasks.

## Results (suite version 1)

| Task | Files | Lines | Declarations | Concepts | Bindings | JS | Internals |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| static page | 3 | 68 | 1 | 17 | 1 | 0 | none |
| component | 3 | 88 | 1 | 20 | 1 | 0 | none |
| form | 3 | 109 | 2 | 26 | 2 | 0 | none |
| validation | 3 | 163 | 2 | 39 | 2 | 0 | none |
| enhanced action | 3 | 152 | 4 | 32 | 2 | 0 | none |
| deferred region | 4 | 145 | 3 | 32 | 2 | 9 | none |
| live update | 3 | 98 | 2 | 21 | 1 | 0 | none |
| custom JavaScript | 4 | 103 | 1 | 22 | 1 | 8 | none |
| island | 4 | 105 | 2 | 21 | 1 | 18 | none |


Issue #214 removes unused action contexts and the validation task's separate draft-text argument.
The form drops from 114 to 109 lines and 27 to 26 concepts; validation drops from 170 to 163 lines
and 40 to 39 concepts.

Issue #215 moves render identity and ordering to host-owned sessions. Enhanced actions drop from
188 to 152 lines and 37 to 32 concepts; deferred regions from 134 to 129 lines and 33 to 31 concepts;
live updates from 174 to 98 lines and 31 to 21 concepts; islands from 109 to 105 lines and 24 to 21
concepts. These tasks have no protocol imports or module-level secrets. The live task has one route
binding (formerly three) and no application JavaScript (formerly 25 lines). Enhanced actions also
use the shipped module directly (2 application JavaScript lines removed).

Issue #216 adds a native full-page forecast GET and a `noscript` link inside the loading region.
The deferred task grows from 129 to 145 lines and 31 to 32 concepts, with no new route binding,
annotation or JavaScript. Native and enhanced responses share the same loader and renderer.

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

- Static pages, components, forms and validation need no protocol or dev-runtime types and have no
  known host-internal gaps.
- Native and enhanced actions share one action and one outcome model; the enhanced task adds regions,
  not a second handler.
- Enhanced pages use host-owned render sessions; identity and revision state no longer leak into
  commands. Deferred streams still use explicit fetch JavaScript, but the same content is now
  reachable without JavaScript through a normal full-page GET (#216).

## Follow-up

- [#213](https://github.com/christian-draeger/woge/issues/213) Built-in same-origin request context for POST actions — done
- [#214](https://github.com/christian-draeger/woge/issues/214) Optional action context, `FormValues` for business-rule errors, clearer duplicate-ID errors — done
- [#215](https://github.com/christian-draeger/woge/issues/215) Hide identity, epoch and revision plumbing; simpler live regions — done
- [#216](https://github.com/christian-draeger/woge/issues/216) No-JavaScript fallback for deferred regions — done

When another follow-up lands, update the task, regenerate the baseline and update this table.

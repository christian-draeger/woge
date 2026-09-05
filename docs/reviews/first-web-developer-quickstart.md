# First web-developer quickstart terminology review

- Date: 2026-09-05
- Scope: Spring Boot WebFlux quickstart from a fresh repository checkout
- Perspective: structured maintainer proxy review using the documented target reader—comfortable with
  HTML, CSS, JavaScript and HTTP, but new to Kotlin
- Evidence: canonical example compilation, real-server integration test and no-JavaScript walkthrough

This is the first terminology pass, not a substitute for an independent usability study before beta.
Each confusing point below has either been fixed in #73 or assigned to a concrete follow-up.

| Observation | Resolution |
| --- | --- |
| `element("h1")` looked like a string-template API and hid tag completion | Added thin common-tag wrappers such as `h1 {}`, `form {}` and `table {}`; standards-derived expansion is tracked by [#131](https://github.com/christian-draeger/woge/issues/131) |
| `suspend`, receiver lambdas, named arguments and `?: return` interrupted the web task | Added the task-scoped [Kotlin bridge](../guides/kotlin-for-web-developers.md) and avoided a general language detour |
| `PageUseCase` and `DeferredRegionsUseCase` sounded framework-internal | The guide defines them once as the host-neutral application port and shows Spring routes separately |
| Page epoch, region ID and patch URL reconstruction is visible but repetitive | Keep it explicit in the first slice; generated host-neutral descriptors remain owned by [#27](https://github.com/christian-draeger/woge/issues/27) |
| Copying browser source into example resources is not a normal consumer installation story | The guide labels it development-only; versioned browser-package consumption is tracked by [#132](https://github.com/christian-draeger/woge/issues/132) |
| A loading shell could be mistaken for the no-JavaScript end state | Added a visible GET form and `noscript` link to the complete server-rendered response, plus an explicit disable-JavaScript walkthrough |

The first independent reviewer should run only the public quickstart and record time-to-page, terms
looked up, compiler errors, browser observations and whether the fallback was discoverable. Repeated
problems feed [#74](https://github.com/christian-draeger/woge/issues/74) and the consumer AI-DX run in
[#93](https://github.com/christian-draeger/woge/issues/93).

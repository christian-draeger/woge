# Documentation style guide

Woge documentation is written first for a web developer who knows HTML, CSS, JavaScript, URLs, requests and browser developer tools but may be opening a Kotlin project for the first time.

## Start from the web

- Say `form`, `link`, `HTTP response`, `HTML attribute` and `CSS class` when those are the real concepts.
- Explain what Woge adds and what the browser already does.
- Do not use mobile, Compose or virtual-DOM terminology as the default analogy.
- Show the no-JavaScript behavior before explaining enhancement when both exist.
- Keep ordinary HTML, CSS and JavaScript escape hatches visible.

## Introduce Kotlin just in time

Explain only syntax required by the current example. A page guide may briefly introduce a function, lambda and named argument; it should not detour into a complete language tutorial.

When a Kotlin feature provides the safety being discussed, name the benefit in plain language:

| Kotlin/Woge term | Web-oriented explanation |
| --- | --- |
| `data class` | A typed request or form value with named fields |
| `suspend fun` | A function that can wait for work without owning a thread while it waits |
| `Flow<T>` | A sequence of values that can arrive over time |
| generated descriptor | A compiler-checked reference replacing a string URL, action or DOM target |
| sealed result | A fixed set of outcomes the compiler requires the caller to handle |

Link to deeper Kotlin material after the reader has completed the web task.

## Structure a guide

1. State the visible result.
2. Show the smallest complete example with imports and host setup available nearby.
3. Explain the web request and response behavior.
4. Explain the Kotlin/Woge pieces introduced by the example.
5. Show failure, accessibility and security behavior where relevant.
6. Point to one next task and the exact API reference.

Prefer one concept per example. Add an advanced composition only after the simple path works.

## Keep examples trustworthy

- Store each canonical example as executable source and include it in CI.
- Do not maintain almost-identical snippets in several pages.
- Include imports when ambiguity would make copying fail.
- State the supported Woge and host versions.
- Mark proposed or unimplemented APIs explicitly.
- Show compiler errors using their real diagnostic text after diagnostics exist.

## Writing rules

- Lead with what the developer achieves.
- Use short sentences and concrete nouns.
- Define a Woge-specific term on first use.
- Prefer a runnable example over a feature inventory.
- Do not describe defaults as magic; name the convention and override point.
- Do not claim simplicity by hiding network, security or browser behavior.

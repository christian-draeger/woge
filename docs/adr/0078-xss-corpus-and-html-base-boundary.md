# ADR 0078: Verify XSS boundaries with one corpus and reject generic base elements

- Status: Accepted
- Date: 2026-10-11
- Decision owners: Woge maintainers
- Related issues: [#42](https://github.com/christian-draeger/woge/issues/42), [#43](https://github.com/christian-draeger/woge/issues/43)

## Context

Woge already has context-aware HTML encoding, typed URL values, explicit unsafe HTML capabilities, patch-content checks and same-origin navigation defaults. Those controls need one shared regression set so native rendering, protocol validation and browser DOM application cannot silently drift. A generic `<base>` element is especially risky: it can change the destination of relative links and forms after those values were validated.

## Decision

Maintain a small versioned XSS corpus under `testing/xss-corpus/` and consume it from Kotlin rendering/protocol tests and fallback-client browser tests. Test text, attribute, URL, style-attribute, patch and foreign-namespace contexts. The typed DSL has no `base()` function, so writing one fails to compile, and the generic `element("base")` path also rejects it; an application that needs a document base must own that document-level policy explicitly. Enhanced navigation continues to validate response targets as same-origin, independently of document base resolution.

Raw HTML and active attribute/URL escape hatches remain explicitly opt-in trusted-code APIs. The annotation and wrapper identify an audit point, not a sanitizer or proof that content is safe.

## Alternatives considered

- **Allow arbitrary base URLs through the ordinary URL API:** rejected because even a syntactically safe URL can retarget relative application links and actions.
- **Duplicate payload lists in the JVM and JavaScript suites:** rejected because copies drift and produce inconsistent regression coverage.
- **Sanitize all strings with one general-purpose HTML filter:** rejected because HTML, URL, CSS and script contexts have different rules and Woge does not claim to provide a general sanitizer.

## Consequences

### Positive

- Native HTML, patch encoding and browser patching share the same regression corpus.
- Relative action/link targets cannot be silently redirected by generic DSL output.
- Unsafe HTML capabilities remain visible and reviewed rather than being mistaken for sanitization.

### Negative

- Applications that intentionally need `<base>` must author it outside the generic safe DSL and own its URL policy.
- The corpus is a regression set, not exhaustive browser fuzzing; raw HTML, user-supplied CSS, strict CSP and Trusted Types remain separate risks.

## Follow-up

- Run the shared corpus in Kotlin and browser CI for [#42](https://github.com/christian-draeger/woge/issues/42).
- Track strict CSP and Trusted Types defense in depth in [#43](https://github.com/christian-draeger/woge/issues/43).

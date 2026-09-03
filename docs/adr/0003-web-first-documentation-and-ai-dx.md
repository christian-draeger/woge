# ADR 0003: Use web-first documentation and compiler-guided AI DX

- Status: Accepted
- Date: 2026-09-03
- Decision owners: Woge maintainers
- Related issues: [#70](https://github.com/christian-draeger/woge/issues/70), [#71](https://github.com/christian-draeger/woge/issues/71), [#72](https://github.com/christian-draeger/woge/issues/72), [#74](https://github.com/christian-draeger/woge/issues/74)

## Context

The primary audience includes web developers who understand browser and HTTP concepts but have limited Kotlin experience. Coding models are also expected to create Woge applications reliably. Separate human and AI APIs would duplicate concepts and make correctness harder to evaluate.

## Decision

Documentation starts from familiar web concepts and introduces Kotlin syntax only when the current task needs it. Examples are canonical source files compiled or executed in CI, not copied snippets that can drift.

Woge has one public application API for humans and coding models. It favors small orthogonal concepts, explicit types, deterministic generation and defaults, complete examples, and actionable source-located diagnostics. AI-assisted output must pass the same compiler, browser, accessibility and security checks as human-authored output.

AI DX is evaluated with versioned, model-neutral tasks and measurable outcomes. Vendor-specific prompt conventions do not enter the public API.

## Alternatives considered

- **Kotlin-first reference documentation only:** rejected because it assumes knowledge the target audience may not have.
- **Separate AI-facing DSL or metadata API:** rejected because it creates a second product contract and may weaken type safety.
- **Documentation-only AI claims:** rejected because reliability requires compile, test and repair measurements.

## Consequences

### Positive

- Web developers can transfer existing knowledge directly.
- Compiler feedback supports both learning and automated repair.
- Canonical examples expose documentation drift in CI.

### Negative

- Documentation and negative compile fixtures are implementation work, not a release afterthought.
- Diagnostics and generated naming need compatibility discipline.
- Model-based evaluation can vary, so deterministic checks remain the release gate.

## Follow-up

- Apply the writing rules in the [documentation style guide](../documentation/style-guide.md).
- Define and baseline the evaluation in the [AI-DX criteria](../ai-dx/evaluation.md).

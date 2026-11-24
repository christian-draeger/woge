# ADR 0119: Defer an application feature generator

- Status: Accepted
- Date: 2026-11-24
- Decision owners: Woge maintainers
- Related issues: [#155](https://github.com/christian-draeger/woge/issues/155)

## Context

Adding a first typed form involves application decisions about the route, form validation, error
text, persistence and host binding. The versioned Spring Boot scaffold already covers the Gradle and
host startup. We evaluated how much a separate feature recipe could remove from the next step.
[Quick-add experiment](../../spikes/vertical-feature-recipe/README.md)

## Decision

Retain one fixed, reviewable recipe in the frozen spike. Its source belongs to the application after
application of the patch. KSP retains ownership of the deterministic typed descriptors. Defer a
supported feature generator until repeated consumer work establishes what should be parameterized
and how updates should respect existing application changes.

## Alternatives considered

- A CLI or Gradle task could prompt for a name and create the same files. The trial found no need
  beyond three fixed file additions, and it would add another tool or build integration to maintain.
- Putting a second feature in the default scaffold would make every new application own an extra
  route, test and domain choice. A separate recipe lets users choose a relevant example.

## Consequences

The recipe copies the same reviewed source for users and coding tools. Its collision behavior is
visible and leaves existing files untouched. Application authors still design validation and
persistence and own evolving that code. The measured trial supplies a repeatable functional check.
It leaves authoring-time comparisons and independent comprehension open.

## Follow-up

Use the existing [human and agent evaluation](../ai-dx/evaluation.md) to identify recurrent feature
setup work. Reconsider a supported recipe format if evidence shows repeatable savings across
independent consumers and both update and conflict handling can be verified.

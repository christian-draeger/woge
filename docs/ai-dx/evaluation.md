# AI-assisted developer-experience criteria

AI friendliness in Woge means that a coding model can discover the normal API, produce a small correct application, and repair mistakes using compiler and test feedback. It does not mean adding an AI-specific framework layer.

## Design criteria

Public APIs should provide:

- small concepts with one clear responsibility;
- strong input, output and reference types;
- deterministic generated names and source;
- one obvious default form before advanced variants;
- complete, compile-verified examples;
- source-located errors that state the violated rule, received shape and a valid shape;
- stable, searchable diagnostic identifiers where practical;
- explicit security, accessibility and no-JavaScript behavior.

Avoid magic strings, hidden global state, ambiguous overloads and several spellings for the same operation unless evidence justifies them.

## Evaluation tasks

The versioned [v0.1 corpus](corpus-v0.1.md) asks a participant to:

1. serve a standards-compliant page with Spring Boot;
2. add a typed route and ordinary link;
3. submit and validate a typed form action;
4. stream a deferred region;
5. update two typed regions from one action;
6. add an authorized live update;
7. diagnose and repair deliberately invalid component and action signatures;
8. style a responsive screen with ordinary CSS and with the optional Tailwind path.

Each task starts from the same published consumer scaffold and public documentation. Hidden repository context or maintainer-only prompts are not allowed.

## Measurements

Record:

- whether the clean consumer project compiles;
- whether unit, adapter and browser tests pass;
- unsafe or inaccessible behavior introduced;
- invented APIs or dependencies;
- number of compile-and-repair iterations;
- unnecessary application code and duplicated concepts;
- whether normal HTML and HTTP behavior remains intact.

Human-authored and AI-assisted solutions have the same correctness gates. Model runs are evidence collected at milestones, not nondeterministic pull-request CI requirements.

## Run protocol

1. Start from a clean copy of the named published scaffold. Record its version, Woge version, JDK, Kotlin, Gradle, operating system and documentation commit.
2. Give the participant only the canonical task, public documentation and normal compiler/test output. Record any additional clarification verbatim.
3. Commit after each task so changed files, dependencies and application lines are attributable.
4. Run the same deterministic compiler, unit, adapter, browser, accessibility and security checks for every participant.
5. Record outcome and counts with the [result template](results/README.md). Do not store secrets, private prompts or model chain-of-thought.
6. Treat a hard-gate failure as a failed task even if the screen appears correct. Do not average unsafe output into a passing score.

Correction iterations count participant edits followed by a compiler or test run after the first submitted solution. Clarifying a task before editing is not a correction. An invented API is a referenced Woge type, function, annotation, configuration key or artifact that does not exist in the evaluated version. Unnecessary code is reported as added production files, dependencies and nonblank application lines relative to the human control; it is evidence for review, not an automatic failure.

## Result interpretation

| Signal | Pass expectation |
| --- | --- |
| Compile and deterministic tests | All pass |
| Security/accessibility/no-JavaScript hard gates | No regression or bypass |
| Invented Woge API | Zero in the final solution |
| Corrections | Recorded per task; repeated patterns become diagnostic or documentation issues |
| Added dependencies and code | No unexplained dependency; material excess over the control is reviewed |
| Adapter portability | Shared application source remains unchanged across required hosts |

The rubric rewards small orthogonal APIs, explicit types, deterministic defaults and actionable diagnostics by measuring whether participants discover the canonical path and repair invalid code. It does not reward generated-code volume or framework-specific shortcuts.

## Evaluation policy

- Keep tasks and expected observable outcomes under version control.
- Use more than one model family when practical, but never shape the public API around a vendor prompt format.
- Convert repeated failure patterns into API, diagnostic or documentation issues.
- Rerun the corpus before MVP API freeze and each compatibility release.
- Publish limitations and methodology with results.

The [pre-API control](results/2026-09-03-pre-api-control.md) establishes the initial comparison and concrete design improvements. The first scored consumer run is tracked by [#93](https://github.com/christian-draeger/woge/issues/93) because it requires the published M1 scaffold.

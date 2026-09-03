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

The initial corpus will ask a participant to:

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

## Evaluation policy

- Keep tasks and expected observable outcomes under version control.
- Use more than one model family when practical, but never shape the public API around a vendor prompt format.
- Convert repeated failure patterns into API, diagnostic or documentation issues.
- Rerun the corpus before MVP API freeze and each compatibility release.
- Publish limitations and methodology with results.

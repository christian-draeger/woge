# Todo human/agent evaluation corpus v0.1

This protocol extends the historical M1 corpus with application changes over the same
[published Todo consumer](../../examples/todo-spring-boot/README.md). It is a task definition,
not a completed human or model evaluation. Model calls never run in pull-request CI.

## Starting state and gates

Use one clean standalone application copy per participant. Record source commit, published Woge/
scaffold version, public documentation revision, generated/application guidance, JDK/Kotlin/Gradle,
OS, browser versions and tool configuration. Allow dependency downloads before task timing and
report their cold setup separately. Participants receive only the application and public docs.

Run the same direct/HTTP/browser checks for every participant. Record the unmodified baseline first.
Use deterministic test data; preserve unsuccessful attempts and visible clarification/correction
logs. Do not record credentials, personal data or hidden model reasoning.

## Tasks

| ID | Request | Observable acceptance |
| --- | --- | --- |
| TDX-01 | Add an optional detail note to each todo | Saved in the in-memory repository, displayed escaped, retained through complete/filter, useful native and enhanced flows |
| TDX-02 | Rename the Kotlin title property to text while preserving the existing serialized form field title | Generated/typed references compile, old native clients still submit title, labels and validation still target the same control |
| TDX-03 | Add a lower-bound title rule and its clear validation message | A focused failing behavior test becomes passing; native/enhanced paths preserve text and produce equivalent field/summary errors |
| TDX-04 | Upgrade between two actually published Woge versions | Clean resolution, regeneration, tests and production artifact pass; migration changes and diagnostics are recorded |

Do not invent two releases for TDX-04. Mark it blocked until two suitable published versions exist,
or record an explicitly separate candidate-build experiment with its limits.
For TDX-02, use the serializer's supported rename contract and the documented field metadata API;
do not silently change the wire name to satisfy compilation.

## Record per participant and task

- Participant kind and experience; model family/version when applicable.
- Task start/end, cold setup, time to visible result and measured cost where available.
- Final compile/test/browser outcomes and exact commands.
- Guessed APIs, compiler failures, correction iterations and human/maintainer interventions.
- Files/declarations/imported Woge concepts, handwritten bindings and added production code/dependencies.
- Unsafe behavior, semantic/accessibility failures and whether any task was blocked or incomplete.
- Commit/workspace per task and pointers to test reports, browser traces and public issue findings.

Compare more than one model family and the human-authored control in #181 on the same version.
A maintainer control is allowed only with that limitation stated. Reuse the themed task protocol
for #253 rather than treating the small Todo corpus as a complex-screen or assistive-technology audit.

## Follow-up and claims

Repeated friction feeds #266/#267; development-tool comparisons feed #273. Human usability remains
owned by #49, the independent release decision by #50. Publish measured successes and failures
before making comparative productivity claims. Shorter code or an AGENTS.md file alone is not
evidence of better outcomes.

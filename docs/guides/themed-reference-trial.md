# Human-run reference extension trial

This is a **protocol, not a completed evaluation**. Run once with a web developer new to Woge and
once with a developer using an AI assistant. Live model trials belong to issue #93, not PR CI.

## Setup

Give each participant a clean checkout, a running reference host, and only the published guides/index.
Start at the [themed screen walkthrough](themed-reference-screen.md). Do not supply private prompts,
maintainer advice or hidden APIs. Use a separate throwaway branch/worktree for each trial.
Record the commit, host, browser, OS, Kotlin/JDK, experience level, time limit (suggested 45 minutes),
and, for the assisted run, model/version, tool configuration and the complete prompt/interaction log.
Do not record secrets or personal data.

## Tasks

1. Add an optional **delivery note** field; persist it in the demo model and show it in task details.
   Keep create/edit, filtering and no-JavaScript flows useful.
2. Customize the installed disclosure recipe to show a team-specific handbook label and border token.
   Keep its stable scope marker and lock provenance. Do not modify Woge core.
3. Give the new detail content distinct branded styling and an ordinary CSS equivalent, without
   duplicating the semantic renderer, action or model. Rebuild Tailwind from documented commands.
4. Check 320-pixel layout, dark/high contrast/reduced motion, keyboard modal focus and the no-JS form.
   Run the documented focused tests and byte budgets. Explain what is still unverified.

## Record and follow up

Record time to first useful HTML, completion/failure per task, docs/links used, missing examples,
incorrect APIs suggested, compile/runtime errors and recovery steps, source changes, tests/results,
asset/timing deltas, confidence and any maintainer intervention. Keep unsuccessful attempts, not only
final output. Compare plain-CSS and branded paths and note whether the participant understood source
ownership, native browser behavior and patch identity.

For IntelliJ, separately record inspections/completion in the canonical CSS literals from the CSS
and assets guide, plus IDE/plugin versions. Run a separate screen-reader/manual high-contrast audit;
automated browser checks are not that audit. Summarize findings in ADR 0102's Follow-up and link
artifacts from #85/#93 before using them in the 1.0 compatibility/catalog decision.

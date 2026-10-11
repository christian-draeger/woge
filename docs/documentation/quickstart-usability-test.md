# Quickstart usability test (human facilitator)

**Not yet performed.** Recruit a web developer who knows HTML/forms but little Kotlin. Use a fresh
checkout with JDK 21; allow dependency downloads before timing. Give them only the
[quickstart](../guides/quickstart-spring-boot.md), a terminal and browser. Do not coach unless blocked.

1. Start a five-minute timer. Ask them to run `/notes` and add a note with JavaScript disabled.
2. Enable JavaScript and submit another note. Ask what changed in the HTTP traffic, and whether
   both paths run the same action. Let them use Network tools.
3. Ask them to find the HTML label, change its text and add a CSS rule; restart and check it.
4. Ask what happens on refresh, a failed POST, a compile error and a missing browser module.
5. Ask which concepts they needed, and which words/imports or setup steps stopped them.

Record OS/JDK, cold-download versus task time, time to first successful submission, blockers,
misunderstood terms and unaided completion. Check keyboard focus and native submission too.
Agree on concrete guide fixes; rerun with another participant. Report observations, not invented
quotes or a pass result. This checklist is preparation, **not evidence of human usability testing**.

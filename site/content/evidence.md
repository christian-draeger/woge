---
title: Try a demo and inspect the evidence
description: Start with a runnable application, then check which versions, workflows and environments the project has actually verified.
---

## Try the published Todo

Run the fixed `0.1.0-demo.1` image on Docker's `linux/amd64` platform. It uses Woge `0.1.0-alpha.1`. Add a todo, submit invalid input, complete it and filter the list. Open a second tab to see an enhanced update while an unsaved draft stays in place. Restarting clears the shared in-memory store. [Run by immutable digest](https://github.com/christian-draeger/woge/blob/main/docs/guides/todo-container.md#run-the-published-demo)

## Explore other maintained examples

- **A project dashboard.** Watch independent sections complete or load the same data as one complete page. The reference has launchers for WebFlux, MVC and Ktor. [Run the reference application](https://github.com/christian-draeger/woge/blob/main/examples/reference-application/README.md) · [Build a local OCI image](https://github.com/christian-draeger/woge/blob/main/examples/reference-application/README.md#build-a-local-reference-image)
- **A feature inside secured Spring MVC.** Book a PetClinic visit through its existing controller, login and validation. The example covers native and enhanced submission with the same persistence write. [Try the PetClinic reference](https://github.com/christian-draeger/woge/blob/main/examples/petclinic-woge/README.md)

## Read results in context

| Evidence | What was observed |
| --- | --- |
| Todo comparison, 20 October 2026 | The tested Woge snapshot and matched htmx fixtures passed the shared native and enhanced browser journeys. Woge's enhanced toggle had higher HTTP p95 in the tested setup. The small visible-interaction sample was inconclusive. [Fixture, versions and raw results](https://github.com/christian-draeger/woge/blob/main/docs/performance/claim-evidence.md#frozen-incremental-todo-comparison) |
| Todo beta candidate, 24 November 2026 | The pinned candidate at `7bf1e3e` passed automated edit and browser checks for three scoped changes. The record covers a specific candidate and agent workflow. [Candidate task evidence](https://github.com/christian-draeger/woge/blob/main/docs/ai-dx/results/2026-11-24-beta-todo-agent.md) |
| Expanded performance study | Matched production deployments and broader browser and load measurements remain research work. [Comparison contract](https://github.com/christian-draeger/woge/blob/main/docs/performance/comparison-contract.md) · [Current study](https://github.com/christian-draeger/woge/issues/293) |

Use the examples to evaluate behavior on a concrete task. Human navigation and comprehension review and independent release review remain open. [Quickstart usability review](https://github.com/christian-draeger/woge/issues/49) · [Release decision](https://github.com/christian-draeger/woge/issues/50)

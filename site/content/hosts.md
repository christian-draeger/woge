---
title: Choose a host and deploy
description: Start with the server that fits your application. Choose one host adapter and keep the same Woge version across its libraries, plugins and browser assets.
---

## Choose a server

| Host | When to start here | Guide |
| --- | --- | --- |
| Spring Boot WebFlux | You want the primary Woge quickstart or already use reactive Spring. | [WebFlux guide](https://github.com/christian-draeger/woge/blob/main/docs/guides/spring-webflux-adapter.md) |
| Spring MVC | You have a Servlet application or want to add Woge to existing MVC routes. | [MVC guide](https://github.com/christian-draeger/woge/blob/main/docs/guides/spring-mvc-adapter.md) |
| Ktor | You want to use Ktor's suspending routes. | [Ktor guide](https://github.com/christian-draeger/woge/blob/main/docs/guides/ktor-adapter.md) |

Each host has an independent launcher for the shared reference application. Use one launcher at a time. [Run the same app on another host](https://github.com/christian-draeger/woge/blob/main/examples/reference-application/README.md#run-another-host) · [Tested host contracts](https://github.com/christian-draeger/woge/blob/main/docs/architecture/server-adapter-parity.md)

## Choose a version

| Distribution | What it means |
| --- | --- |
| Source checkout | Guides and examples follow the checked-out commit. Some APIs can be newer than packaged releases. |
| Maven Central snapshot | `0.1.0-SNAPSHOT` resolves anonymously and can change between builds. Record the resolved build for a repeatable evaluation. |
| Published alpha | `0.1.0-alpha.1` is available from GitHub Packages with authentication. |
| Fixed beta pilot | The `0.1.0-beta.1` kit is an evaluated candidate at `7bf1e3e`. Its release decision and public registry publication are pending. |

[Install Woge](https://github.com/christian-draeger/woge/blob/main/docs/guides/packages.md) · [Pilot and release decision](https://github.com/christian-draeger/woge/issues/50) · [Compatibility and upgrades](https://github.com/christian-draeger/woge/blob/main/docs/guides/compatibility-and-upgrades.md)

For public APIs, start with the guide for your host and task and follow its compiled source links. The publication's documentation JAR contains maintained guides. [Canonical documentation](https://github.com/christian-draeger/woge/blob/main/docs/README.md) · [Build the JVM API reference](https://github.com/christian-draeger/woge/tree/main/site/api-reference) · [Published artifact contents](https://github.com/christian-draeger/woge/blob/main/docs/development/publishing.md)

## Plan a real deployment

Build a JVM application, serve immutable assets and configure your reverse proxy and health checks. Your application owns authentication, authorization and persistence. Evaluate shared sessions, request limits and rolling upgrades before promoting a demo to a real deployment. [Production operations and tested rollout](https://github.com/christian-draeger/woge/blob/main/docs/guides/production-operations.md) · [Request troubleshooting](https://github.com/christian-draeger/woge/blob/main/docs/guides/troubleshooting.md)

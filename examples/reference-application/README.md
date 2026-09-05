# Reference application

This directory is the maintained consumer of Woge's public modules. Its project-operations domain and
journeys are defined in [ADR 0004](../../docs/adr/0004-project-operations-reference-application.md).

Run its first host with:

```shell
./gradlew :woge-reference-spring-webflux:bootRun
```

Then open `http://localhost:8080/projects/woge`.

[`shared`](shared) contains the host-neutral `ProjectPage`, semantic HTML and region work.
[`spring-webflux`](spring-webflux) contains only Spring Boot startup, functional routes, static assets
and the real-server integration test. The example consumes root projects and is verified by
`./gradlew check`; it is executable documentation, not a published Woge artifact.

The [quickstart](../../docs/guides/quickstart-spring-boot.md) explains the observable web behavior.
Issue [#24](https://github.com/christian-draeger/woge/issues/24) adds MVC and Ktor launchers around the
same `shared` code.

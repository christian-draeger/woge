# Reference application

This directory is the maintained consumer of Woge's public modules. Its project-operations domain and
journeys are defined in [ADR 0004](../../docs/adr/0004-project-operations-reference-application.md).

Run a maintained host with:

```shell
./gradlew :woge-reference-spring-webflux:bootRun
./gradlew :woge-reference-spring-mvc:bootRun
./gradlew :woge-reference-ktor:run
```

Then open `http://localhost:8080/projects/woge`.

[`shared`](shared) contains the host-neutral `ProjectPage`, semantic HTML, region work and web assets.
[`spring-webflux`](spring-webflux), [`spring-mvc`](spring-mvc) and [`ktor`](ktor) contain only their
host-specific startup, routes and real-server integration tests. The example consumes root projects
and is verified by `./gradlew check`; it is executable documentation, not a published Woge artifact.

The [quickstart](../../docs/guides/quickstart-spring-boot.md) explains the observable web behavior.
Issue [#24](https://github.com/christian-draeger/woge/issues/24) adds the final cross-host browser gate
around the same `shared` code.

# Spring HTML + htmx baseline

This time-boxed spike implements the Woge reference journey without Woge. It is evidence for framework design, not production application code.

## What it proves

- one immediate semantic HTML shell with three independently loaded regions;
- a complete-page link for users without JavaScript;
- native forms with Post/Redirect/Get;
- enhanced htmx forms with validation and out-of-band multi-region updates;
- the same domain state and Thymeleaf templates on Spring MVC and Spring WebFlux;
- HTTP contract tests for both hosts.

The spike uses Spring Boot 4.1.1, Kotlin 2.4.0, Java 17 bytecode and htmx 2.0.10. The recently released htmx 4 line is intentionally excluded until its migration cost and production adoption are clearer.

## Run it

From this directory:

```shell
./gradlew test
./gradlew :spring-mvc:bootRun
./gradlew :spring-webflux:bootRun
```

The MVC application uses <http://localhost:8080/projects/woge>. The WebFlux application uses <http://localhost:8081/projects/woge>. Run one `bootRun` task at a time from a terminal, or run the two commands in separate terminals.

Add `?full=true` to request the complete page in one response. This is also the explicit no-JavaScript path linked from the immediate shell.

## Structure

- `shared` owns the in-memory fixture, semantic HTML templates and plain CSS;
- `spring-mvc` owns blocking host glue;
- `spring-webflux` owns coroutine-based reactive host glue;
- `evidence.md` records measurements, gaps and design recommendations.

Reproduce the source measurements with:

```shell
./measure.sh
```

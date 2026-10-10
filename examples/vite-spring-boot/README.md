# Vite example

A small Spring Boot WebFlux page with a TypeScript enhancement built by Vite through the optional
`dev.woge.vite` plugin. It shows:

- a server-rendered list that works without JavaScript;
- `main.ts` importing plain CSS and loading a second module (`chart.ts`) only on demand;
- production files content-hashed like every other static file;
- `wogeDev` running the Vite dev server next to the application.

Read the [Vite guide](../../docs/guides/vite.md) for the full explanation.

This is a standalone project, like the [scaffold](../../scaffolds/spring-boot/README.md). It is not
part of the root build. `./gradlew testViteExample` in the Woge repository copies it, builds it
against freshly published Woge artifacts, checks the hashed output and runs it under `wogeDev`.

To try it by hand, publish Woge locally (`./gradlew publishScaffoldArtifacts`), copy `gradlew` and
`gradle/wrapper/` from the Woge repository into this folder, then run:

```shell
npm ci
./gradlew wogeDev -PwogeRepository=/path/to/woge/build/scaffold-maven-repository
```

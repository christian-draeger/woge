# Tailwind example

A small Spring Boot WebFlux page styled with Tailwind through the optional `dev.woge.tailwind`
plugin. It shows:

- class names chosen with a Kotlin `when`, so Tailwind sees every one of them;
- design tokens as CSS custom properties with light, dark and high-contrast values;
- `motion-safe:` for users who prefer reduced motion;
- plain CSS (`static/site.css`) with nesting and container queries next to Tailwind.

Read the [Tailwind guide](../../docs/guides/tailwind.md) for the full explanation.

This is a standalone project, like the [scaffold](../../scaffolds/spring-boot/README.md). It is not
part of the root build. `./gradlew testTailwindExample` in the Woge repository copies it, builds it
against freshly published Woge artifacts and checks the generated, hashed stylesheet.

To try it by hand, publish Woge locally (`./gradlew publishScaffoldArtifacts`), copy `gradlew` and
`gradle/wrapper/` from the Woge repository into this folder, then run:

```shell
npm ci
./gradlew wogeDev -PwogeRepository=/path/to/woge/build/scaffold-maven-repository
```

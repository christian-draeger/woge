# Executable examples

Maintained documentation examples live below this directory and join the root verification build once
they are executable. They use supported public APIs and compile in `./gradlew check`.

Do not copy spike packages into examples. The first maintained consumer is the
[reference application](reference-application/README.md), whose host-neutral page and Spring Boot
WebFlux, Spring MVC and Ktor launchers are part of the root build.

The compact [M1 API corpus](m1-api-corpus/README.md) compiles complete positive examples for every M1
boundary and verifies deliberately invalid API shapes against the pinned Kotlin compiler.

The [Tailwind example](tailwind-spring-boot/README.md) is a standalone project like the scaffold. It
is built against published Woge artifacts by `./gradlew testTailwindExample`.

The [Vite example](vite-spring-boot/README.md) is standalone too. `./gradlew testViteExample` builds
it and runs it under `wogeDev` with the Vite dev server.

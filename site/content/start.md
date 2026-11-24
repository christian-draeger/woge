---
title: Run a page and form
description: Start the maintained notes app, try the same form with and without JavaScript, then choose a starting point for your own project.
---

## Run from source

Use JDK 21 and the Gradle wrapper. Start the Spring Boot WebFlux reference from your Woge checkout.

```sh
./gradlew :woge-reference-spring-webflux:bootRun
```

Open `http://localhost:8080/notes`. Use Ctrl+C to stop the app. The checkout supplies the Gradle wrapper, Kotlin and Spring versions. First-time setup downloads dependencies. [Full quickstart](https://github.com/christian-draeger/woge/blob/main/docs/guides/quickstart-spring-boot.md) · [Get the source](https://github.com/christian-draeger/woge)

## Follow one form submission

1. Disable JavaScript, reload, type a note and select **Add note**. The browser posts an ordinary form.
2. The server validates the note, saves it and replies with **303 See Other**. The browser fetches the current list with a GET. Reloading the list repeats only that GET.
3. Enable JavaScript, reload and add another note. The client uses the same action to update the list and announce success. Inspect the request in your browser's Network tab.

The sample notes share an in-memory store that clears on restart. [Notes source and complete imports](https://github.com/christian-draeger/woge/blob/main/examples/reference-application/shared/src/main/kotlin/dev/woge/example/notes/Notes.kt)

## Start your own project

Use the Spring Boot scaffold to generate a small standalone app with a typed page and plain CSS. It defaults to WebFlux and can select MVC. Its current default resolves a moving Maven Central snapshot anonymously. Follow the scaffold instructions to generate, build and start the app. [Create a standalone project](https://github.com/christian-draeger/woge/blob/main/scaffolds/spring-boot/README.md) · [Package and dependency versions](hosts.md#choose-a-version)

For a first look with Docker and no local JDK, run the fixed Todo image. [Try the published Todo](evidence.md#try-the-published-todo)

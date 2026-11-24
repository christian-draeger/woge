---
title: Build web apps around working HTML
description: Woge is a Kotlin framework for teams that keep application state on the server and want useful pages, links and forms from the first response.
---

[Run your first app](start.md) [Try the published Todo](evidence.md#try-the-published-todo)

## What changes when you turn on JavaScript?

Open the notes example. With JavaScript disabled, adding a note posts a normal HTML form and redirects to the current list. Enable JavaScript and submit again. The same server action updates the list and confirms that it saved the note without reloading the page. [Follow the notes example](https://github.com/christian-draeger/woge/blob/main/docs/guides/quickstart-spring-boot.md)

## Where to start

- **Write a page in Kotlin.** Render semantic HTML and use ordinary stylesheets. Add typed routes and form actions when the page needs them. [Everyday tasks](tasks.md)
- **Use the server you know.** The reference application shares its pages and actions across Spring Boot WebFlux, Spring MVC and Ktor. [Hosts and deployment](hosts.md)
- **Add browser behavior when it helps.** Update parts of a server-rendered page or keep a list current across tabs. A browser-owned widget can live alongside the HTML. [Progressive enhancement](https://github.com/christian-draeger/woge/blob/main/docs/guides/mental-model.md)

## Where it fits

Try Woge on a server-owned workflow such as a form, operations board or account screen. An existing template and fragment setup may suffice. For offline work or editing dominated by rich local state, evaluate a client-owned approach against that workflow. [When to try Woge](https://github.com/christian-draeger/woge/blob/main/docs/product/choosing-woge.md)

Woge is pre-1.0. These pages describe the current source. Published releases, moving snapshots and fixed pilots have separate evidence. [Versions and support](hosts.md#choose-a-version)

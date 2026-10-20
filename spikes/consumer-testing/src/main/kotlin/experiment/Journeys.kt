package experiment

import codes.draeger.playwright.core.playwright
import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.Tracing
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import com.microsoft.playwright.options.AriaRole
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.Executors

fun main(args: Array<String>) {
    val url = requireNotNull(System.getenv("WOGE_TODO_URL")) { "Set WOGE_TODO_URL to the running Todo app" }
    val output = Path.of("build/evidence")
    Files.createDirectories(output)
    if ("--failure-probe" in args) {
        failureProbe(url, output)
        return
    }
    val engines = args.filterNot { it.startsWith("--") }.ifEmpty { listOf("chromium", "firefox", "webkit") }
    val urls =
        System
            .getenv("WOGE_TODO_URLS")
            ?.split(',')
            ?.associate { entry ->
                entry.substringBefore('=') to
                    entry.substringAfter('=')
            }.orEmpty()
    if ("--parallel" in args) {
        Executors.newFixedThreadPool(engines.size).use { executor ->
            engines
                .map { engine ->
                    executor.submit { runEngine(engine, urls[engine] ?: url, output) }
                }.forEach { it.get() }
        }
    } else {
        engines.forEach { runEngine(it, url, output) }
    }
}

private fun runEngine(
    engine: String,
    url: String,
    output: Path,
) {
    for (dsl in listOf(false, true)) {
        val start = System.nanoTime()
        if (dsl) {
            playwright { upstream ->
                browserType =
                    when (engine) {
                        "chromium" -> upstream.chromium()
                        "firefox" -> upstream.firefox()
                        "webkit" -> upstream.webkit()
                        else -> error("Unknown browser $engine")
                    }
                browser { browser -> runJourneys(browser, url, output, "$engine-dsl") }
            }
        } else {
            Playwright.create().use { upstream ->
                val type =
                    when (engine) {
                        "chromium" -> upstream.chromium()
                        "firefox" -> upstream.firefox()
                        "webkit" -> upstream.webkit()
                        else -> error("Unknown browser $engine")
                    }
                type.launch().use { runJourneys(it, url, output, "$engine-direct") }
            }
        }
        println("PASS $engine ${if (dsl) "dsl" else "direct"} ${(System.nanoTime() - start) / 1_000_000}ms")
    }
}

private fun failureProbe(
    url: String,
    output: Path,
) {
    Playwright.create().use { upstream ->
        upstream.chromium().launch().use { browser ->
            browser.newContext().use { context ->
                context.tracing().start(Tracing.StartOptions().setSnapshots(true).setSources(true))
                try {
                    val page = context.newPage()
                    page.navigate(url)
                    val missing =
                        page.getByRole(
                            AriaRole.BUTTON,
                            Page.GetByRoleOptions().setName("Expected missing button"),
                        )
                    try {
                        assertThat(
                            missing,
                        ).isVisible(
                            com.microsoft.playwright.assertions.LocatorAssertions
                                .IsVisibleOptions()
                                .setTimeout(250.0),
                        )
                        error("Expected the assertion to fail")
                    } catch (expected: AssertionError) {
                        val diagnostic = expected.stackTraceToString()
                        check(diagnostic.contains("Expected missing button"))
                        Files.writeString(output.resolve("java-expected-failure.txt"), diagnostic.take(8000))
                        println("PASS expected Java assertion failure: locator diagnostic and source stack captured")
                    }
                } finally {
                    context.tracing().stop(Tracing.StopOptions().setPath(output.resolve("java-expected-failure.zip")))
                }
            }
        }
    }
}

private fun Page.button(name: String) = getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName(name).setExact(true))

private fun Page.link(name: String) = getByRole(AriaRole.LINK, Page.GetByRoleOptions().setName(name).setExact(true))

private fun runJourneys(
    browser: Browser,
    url: String,
    output: Path,
    label: String,
) {
    for (enhanced in listOf(false, true)) {
        browser.newContext(Browser.NewContextOptions().setJavaScriptEnabled(enhanced)).use { context ->
            context.tracing().start(
                Tracing
                    .StartOptions()
                    .setScreenshots(true)
                    .setSnapshots(true)
                    .setSources(true),
            )
            try {
                val page = context.newPage()
                page.navigate(url)
                val title = "Kotlin <HTML> ${UUID.randomUUID()}"
                page.getByLabel("New todo").fill(title)
                page.button("Add todo").click()
                var row =
                    page
                        .locator(
                            "[data-todo-id]",
                        ).filter(
                            com.microsoft.playwright.Locator
                                .FilterOptions()
                                .setHasText(title),
                        )
                assertThat(row).hasCount(1)
                page
                    .locator(
                        "[data-todo-id]",
                    ).filter(
                        com.microsoft.playwright.Locator
                            .FilterOptions()
                            .setHasText(title),
                    ).getByRole(
                        AriaRole.BUTTON,
                        com.microsoft.playwright.Locator
                            .GetByRoleOptions()
                            .setName("Complete")
                            .setExact(true),
                    ).click()
                assertThat(row).containsText("(completed)")
                page.link("Active").click()
                assertThat(row).hasCount(0)
                page.link("Completed").click()
                assertThat(row).hasCount(1)
                row
                    .getByRole(
                        AriaRole.BUTTON,
                        com.microsoft.playwright.Locator
                            .GetByRoleOptions()
                            .setName("Delete")
                            .setExact(true),
                    ).click()
                assertThat(row).hasCount(0)
                page.navigate(url)
                page.getByLabel("New todo").fill("   ")
                page.button("Add todo").click()
                assertThat(page.getByLabel("New todo")).hasValue("   ")
                assertThat(page.getByLabel("New todo")).hasAttribute("aria-invalid", "true")
                assertThat(page.locator("#todo-errors a")).hasAttribute("href", "#todo-title")
                if (enhanced) assertThat(page.locator("#todo-errors")).isFocused()
                page.getByLabel("New todo").fill("Recovered ${UUID.randomUUID()}")
                page.button("Add todo").click()
                assertThat(page.locator("#todo-errors")).hasCount(0)
                if (enhanced) liveJourney(page, context, url)
                println("  $label ${if (enhanced) "enhanced: 3 journeys" else "native: 2 journeys"}")
            } finally {
                context.tracing().stop(Tracing.StopOptions().setPath(output.resolve("$label-$enhanced.zip")))
            }
        }
    }
}

private fun liveJourney(
    page: Page,
    context: BrowserContext,
    url: String,
) {
    val draft = "Draft ${UUID.randomUUID()}"
    page.getByLabel("New todo").fill(draft)
    context.newPage().use { other ->
        other.navigate(url)
        val title = "External ${UUID.randomUUID()}"
        other.getByLabel("New todo").fill(title)
        other.button("Add todo").click()
        assertThat(
            page.locator("[data-todo-id]").filter(
                com.microsoft.playwright.Locator
                    .FilterOptions()
                    .setHasText(title),
            ),
        ).hasCount(1)
        assertThat(page.getByLabel("New todo")).hasValue(draft)
    }
}

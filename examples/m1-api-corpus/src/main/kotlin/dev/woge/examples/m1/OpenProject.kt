package dev.woge.examples.m1

import dev.woge.host.FailureCategory
import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.RequestContext
import dev.woge.host.WogeAction
import dev.woge.host.WogeRoute
import dev.woge.host.failure
import dev.woge.host.redirect
import dev.woge.html.HtmlWriter
import dev.woge.html.button
import dev.woge.html.form

/** A typed command; URL-encoded field decoding belongs to the host's form boundary. */
@WogeRoute("/projects/{project}")
public data class OpenProject(
    val project: String,
)

/** Uses a normal redirect outcome, not a transport-specific response. */
@WogeAction("open-project")
public suspend fun openProject(
    command: OpenProject,
    context: RequestContext,
): PageResult =
    if (command.project.isBlank()) {
        failure(FailureCategory.BAD_REQUEST, context.correlationId)
    } else {
        redirect(OpenProjectRoute.url(command))
    }

/** Generated references remain ordinary native HTML form actions. */
public fun HtmlWriter.projectForm() {
    form(attributes = {
        attribute("method", "post")
        url("action", OpenProjectAction.url)
    }) {
        button(attributes = {
            attribute("name", "project")
            attribute("value", "woge")
        }) { text("Open project") }
    }
}

/** Typed invocation is direct; the registry is an allowlist, not a reflective dispatcher. */
public suspend fun executeProjectAction(
    command: OpenProject,
    context: RequestContext,
): PageResult = OpenProjectAction.execute(PageRequest(command, context))

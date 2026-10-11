package dev.woge.examples.gradient.t08customjavascript

import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.PageUseCase
import dev.woge.host.WogeRoute
import dev.woge.host.htmlPage
import dev.woge.html.applicationUrl
import dev.woge.html.body
import dev.woge.html.button
import dev.woge.html.form
import dev.woge.html.h1
import dev.woge.html.head
import dev.woge.html.html
import dev.woge.html.label
import dev.woge.html.main
import dev.woge.html.meta
import dev.woge.html.metadata
import dev.woge.html.moduleScript
import dev.woge.html.p
import dev.woge.html.stylesheet
import dev.woge.html.textarea
import dev.woge.html.title

private const val MAX_MESSAGE_LENGTH = 140

/** The form is a plain GET: it works without JavaScript and the URL shows the submitted message. */
@WogeRoute("/compose")
public data class ComposeInput(
    val message: String? = null,
)

public class ComposePage : PageUseCase<ComposeInput> {
    override suspend fun open(request: PageRequest<ComposeInput>): PageResult =
        htmlPage {
            doctype()
            html(attributes = { attribute("lang", "en") }) {
                head {
                    meta { attribute("charset", "utf-8") }
                    metadata("viewport", "width=device-width, initial-scale=1")
                    title("Compose · Custom JavaScript")
                    stylesheet(applicationUrl("/assets/custom-javascript/site.css"))
                    moduleScript(applicationUrl("/assets/custom-javascript/counter.js"))
                }
                body {
                    main {
                        h1 { text("Compose a message") }
                        form(attributes = {
                            attribute("method", "get")
                            url("action", ComposeRoute.url(ComposeInput()))
                        }) {
                            label(attributes = { attribute("for", "message") }) { text("Message") }
                            textarea(request.input.message.orEmpty(), attributes = {
                                attribute("id", "message")
                                attribute("name", "message")
                                attribute("maxlength", MAX_MESSAGE_LENGTH.toString())
                                attribute("aria-describedby", "message-hint")
                                data("counter", "message-hint")
                            })
                            p(attributes = { attribute("id", "message-hint") }) {
                                text("Up to $MAX_MESSAGE_LENGTH characters.")
                            }
                            button { text("Preview") }
                        }
                        request.input.message?.let { p { text("Preview: $it") } }
                    }
                }
            }
        }
}

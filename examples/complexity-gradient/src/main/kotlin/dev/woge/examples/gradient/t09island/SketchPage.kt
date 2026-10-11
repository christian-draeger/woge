package dev.woge.examples.gradient.t09island

import dev.woge.host.PageIdentity
import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.PageUseCase
import dev.woge.host.RenderIdentitySecret
import dev.woge.host.WogeRegion
import dev.woge.host.WogeRoute
import dev.woge.host.htmlPage
import dev.woge.host.region
import dev.woge.html.HtmlWriter
import dev.woge.html.applicationUrl
import dev.woge.html.body
import dev.woge.html.h1
import dev.woge.html.head
import dev.woge.html.html
import dev.woge.html.main
import dev.woge.html.meta
import dev.woge.html.metadata
import dev.woge.html.moduleScript
import dev.woge.html.p
import dev.woge.html.stylesheet
import dev.woge.html.title
import dev.woge.protocol.PageEpoch
import java.util.UUID

@WogeRoute("/sketchpad")
public data object SketchInput

public class SketchPage : PageUseCase<SketchInput> {
    private val identitySecret = RenderIdentitySecret.random()

    override suspend fun open(request: PageRequest<SketchInput>): PageResult {
        val identity = PageIdentity(PageEpoch.of("sketch-${UUID.randomUUID()}"), identitySecret)
        return htmlPage {
            doctype()
            html(attributes = { attribute("lang", "en") }) {
                head {
                    meta { attribute("charset", "utf-8") }
                    metadata("viewport", "width=device-width, initial-scale=1")
                    title("Sketchpad · Island")
                    stylesheet(applicationUrl("/assets/island/site.css"))
                    moduleScript(applicationUrl("/assets/island/tally-counter.js"))
                }
                body {
                    main {
                        h1 { text("Sketchpad") }
                        region(SketchPanelRegion.target(identity), "Server note: the page works without JavaScript.")
                    }
                }
            }
        }
    }
}

/** Server-owned text plus one browser-owned island; a patch keeps the island's local count. */
@WogeRegion
internal fun HtmlWriter.sketchPanel(note: String) {
    p { text(note) }
    element("tally-counter", attributes = {
        data("woge-state-key", "tally")
        data("woge-island", "")
    }) {
        p { text("The tally needs JavaScript.") }
    }
}

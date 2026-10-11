package dev.woge.examples.gradient.t01staticpage

import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.PageUseCase
import dev.woge.host.WogeRoute
import dev.woge.host.htmlPage
import dev.woge.html.applicationUrl
import dev.woge.html.body
import dev.woge.html.h1
import dev.woge.html.head
import dev.woge.html.html
import dev.woge.html.main
import dev.woge.html.meta
import dev.woge.html.metadata
import dev.woge.html.p
import dev.woge.html.stylesheet
import dev.woge.html.title

@WogeRoute("/")
public data object StaticPageInput

/** A complete server-rendered document with a stylesheet and no JavaScript. */
public class StaticPage : PageUseCase<StaticPageInput> {
    override suspend fun open(request: PageRequest<StaticPageInput>): PageResult =
        htmlPage {
            doctype()
            html(attributes = { attribute("lang", "en") }) {
                head {
                    meta { attribute("charset", "utf-8") }
                    metadata("viewport", "width=device-width, initial-scale=1")
                    title("Welcome · Static page")
                    stylesheet(applicationUrl("/assets/static-page/site.css"))
                }
                body {
                    main {
                        h1 { text("Welcome to the garden club") }
                        p { text("We meet every Thursday. Bring gloves and a friend.") }
                    }
                }
            }
        }
}

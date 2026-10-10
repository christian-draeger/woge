package example.woge

import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.PageUseCase
import dev.woge.host.WogeRoute
import dev.woge.host.htmlPage
import dev.woge.html.applicationUrl
import dev.woge.html.AssetUrls
import dev.woge.html.body
import dev.woge.html.footer
import dev.woge.html.h1
import dev.woge.html.head
import dev.woge.html.header
import dev.woge.html.html
import dev.woge.html.li
import dev.woge.html.main
import dev.woge.html.meta
import dev.woge.html.metadata
import dev.woge.html.nav
import dev.woge.html.noscript
import dev.woge.html.p
import dev.woge.html.stylesheet
import dev.woge.html.title
import dev.woge.html.ul

@WogeRoute("/")
public data object HomeInput

/** A server-rendered page with normal HTML and no required browser runtime. */
public class HomePage(
    private val assets: AssetUrls,
) : PageUseCase<HomeInput> {
    override suspend fun open(request: PageRequest<HomeInput>): PageResult =
        htmlPage {
            doctype()
            html(attributes = { attribute("lang", "en") }) {
                head {
                    meta { attribute("charset", "utf-8") }
                    metadata("viewport", "width=device-width, initial-scale=1")
                    metadata("description", "A small web-native Woge application")
                    title("Hello from Woge")
                    stylesheet(assets.url(applicationUrl("/styles.css")))
                }
                body {
                    header {
                        nav(attributes = { aria("label", "Primary") }) {
                            text("Woge application")
                        }
                    }
                    main {
                        p(attributes = { classes("eyebrow") }) { text("Spring Boot · Kotlin · HTML") }
                        h1 { text("Hello from Woge") }
                        p { text("This page was rendered on the server with a type-safe HTML DSL.") }
                        ul(attributes = { classes("feature-list") }) {
                            li { text("Normal URLs and HTTP") }
                            li { text("Modern browser CSS") }
                            li { text("Useful without JavaScript") }
                        }
                        noscript {
                            p(attributes = { classes("notice") }) {
                                text("JavaScript is disabled. The complete page still works.")
                            }
                        }
                    }
                    footer { text("Built with web standards, adapted to Spring at the edge.") }
                }
            }
        }
}

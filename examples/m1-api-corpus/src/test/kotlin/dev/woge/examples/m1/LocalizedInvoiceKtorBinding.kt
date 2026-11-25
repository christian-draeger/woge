package dev.woge.examples.m1

import dev.woge.host.LanguageTag
import dev.woge.host.PageUseCase
import dev.woge.host.RequestContext
import dev.woge.ktor.KtorPageInput
import dev.woge.ktor.KtorRequestContextFactory
import dev.woge.ktor.SameOriginKtorRequestContextFactory
import dev.woge.ktor.WogeKtorHandlers
import dev.woge.ktor.ktorMultipart
import dev.woge.ktor.ktorSubmission
import io.ktor.server.application.ApplicationCall
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.request.acceptLanguageItems
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import java.util.Locale

internal fun withKtorInvoiceServer(
    saved: SavedInvoiceTestApp?,
    verify: (Int) -> Unit,
) {
    val server =
        embeddedServer(Netty, host = "127.0.0.1", port = 0) {
            val contexts = KtorRequestContextFactory(::invoiceContext)
            val handlers = WogeKtorHandlers(contexts)
            val page = handlers.page(PageUseCase<Unit> { localizedInvoicePage(it.context) }, KtorPageInput { })
            val submit = handlers.action(localizedInvoiceAction, localizedInvoiceDecoder.ktorSubmission(), contexts)

            routing {
                get(INVOICE_PATH) { page.handle(call) }
                post(ValidateLocalizedInvoiceAction.path) { submit.handle(call) }
                if (saved != null) {
                    val editor = handlers.page(saved.application.page, SavedInvoiceRoute)
                    val save =
                        handlers.action(
                            saved.application.save,
                            savedInvoiceDecoder.ktorMultipart(saved.uploadPolicy),
                            contexts,
                        )
                    get(SavedInvoiceRoute.path) { editor.handle(call) }
                    post(saved.application.save.path) { save.handle(call) }
                    staticResources("/assets/woge-ui", "static/assets/woge-ui")
                }
            }
        }.start(wait = false)
    try {
        val port =
            runBlocking {
                server.engine
                    .resolvedConnectors()
                    .single()
                    .port
            }
        verify(port)
    } finally {
        server.stop(1_000, 1_000)
    }
}

/** The application shares one exact-match locale policy across GET and POST. */
private fun invoiceContext(call: ApplicationCall): RequestContext {
    val base = SameOriginKtorRequestContextFactory.create(call)
    val language =
        call.request
            .acceptLanguageItems()
            .firstNotNullOfOrNull { candidate ->
                candidate.value
                    .lowercase(Locale.ROOT)
                    .takeIf { candidate.quality > 0.0 && it in invoiceLanguages }
            } ?: "en-us"
    return RequestContext(
        method = base.method,
        trace = base.trace,
        language = LanguageTag.of(language),
        headers = base.headers,
        cookies = base.cookies,
        security = base.security,
        mutationIdentity = base.mutationIdentity,
        render = base.render,
    )
}

private val invoiceLanguages = InvoiceLanguage.entries.map { it.tag.lowercase(Locale.ROOT) }.toSet()

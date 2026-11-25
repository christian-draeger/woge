package dev.woge.examples.invoice

import dev.woge.host.LanguageTag
import dev.woge.host.PageUseCase
import dev.woge.host.RequestContext
import dev.woge.ktor.KtorPageInput
import dev.woge.ktor.KtorRequestContextFactory
import dev.woge.ktor.SameOriginKtorRequestContextFactory
import dev.woge.ktor.WogeKtorHandlers
import dev.woge.ktor.ktorMultipart
import dev.woge.ktor.ktorSubmission
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ServerReady
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.request.acceptLanguageItems
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

internal const val INVOICE_PATH = "/localized-invoice"
private const val DEFAULT_PORT = 8080
private const val MAX_PORT = 65_535

public fun main() {
    val port = System.getenv("WOGE_INVOICE_PORT")?.toInt() ?: DEFAULT_PORT
    require(port in 0..MAX_PORT) { "WOGE_INVOICE_PORT must be between 0 and $MAX_PORT." }
    val directory = Path.of(System.getenv("WOGE_INVOICE_DATA") ?: "build/invoice-desk-data")
    val desk = InvoiceDesk(directory)
    val server = embeddedServer(Netty, host = "127.0.0.1", port = port) { invoiceRoutes(desk) }
    server.monitor.subscribe(ServerReady) {
        val connectors = runBlocking { server.engine.resolvedConnectors() }
        val boundPort = connectors.single().port
        println("Invoice desk ready at http://127.0.0.1:$boundPort")
    }
    server.start(wait = true)
}

internal class InvoiceDesk(
    directory: Path,
) {
    val uploads: Path = Files.createDirectories(directory.resolve("uploads"))
    val url = "jdbc:h2:file:${directory.resolve("invoices").toAbsolutePath()}"
    val store = SavedInvoiceStore(url)
    val application = SavedInvoices(store)
    val uploadPolicy = savedInvoiceUploads(uploads)
}

internal fun Application.invoiceRoutes(desk: InvoiceDesk?) {
    val contexts = KtorRequestContextFactory(::invoiceContext)
    val handlers = WogeKtorHandlers(contexts)
    val page = handlers.page(PageUseCase<Unit> { localizedInvoicePage(it.context) }, KtorPageInput { })
    val submit = handlers.action(localizedInvoiceAction, localizedInvoiceDecoder.ktorSubmission(), contexts)

    routing {
        get(INVOICE_PATH) { page.handle(call) }
        post(ValidateLocalizedInvoiceAction.path) { submit.handle(call) }
        if (desk != null) {
            val editor = handlers.page(desk.application.page, SavedInvoiceRoute)
            val save =
                handlers.action(
                    desk.application.save,
                    savedInvoiceDecoder.ktorMultipart(desk.uploadPolicy),
                    contexts,
                )
            get("/") { call.respondRedirect(SavedInvoiceRoute.path) }
            get(SavedInvoiceRoute.path) { editor.handle(call) }
            post(desk.application.save.path) { save.handle(call) }
            staticResources("/assets/woge-ui", "static/assets/woge-ui")
            staticResources("/assets/invoice-desk", "static/assets/invoice-desk")
        }
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

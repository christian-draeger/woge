package dev.woge.ktor

import dev.woge.host.PageRequest
import dev.woge.host.PageUseCase
import io.ktor.server.application.ApplicationCall
import kotlinx.coroutines.CancellationException

/** Executes one portable [PageUseCase] from a Ktor route. */
public class WogeKtorPageHandler<Input : Any> internal constructor(
    private val page: PageUseCase<Input>,
    private val input: KtorPageInput<Input>,
    private val contexts: KtorRequestContextFactory,
) {
    /** Decodes, executes and maps the page without an application-owned transport controller. */
    @Suppress("TooGenericExceptionCaught")
    public suspend fun handle(call: ApplicationCall) {
        val pageRequest = PageRequest(input.decode(call), contexts.create(call))
        val result =
            try {
                page.open(pageRequest)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                call.respondWogePreStreamFailure(failure)
                return
            }
        call.respondWogePage(result)
    }
}

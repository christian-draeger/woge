package dev.woge.ktor

import dev.woge.host.PageRequest
import dev.woge.host.PageUseCase
import dev.woge.host.WogeObservationContext
import dev.woge.host.WogeObserver
import dev.woge.host.WogeOperation
import dev.woge.runtime.observationOutcome
import dev.woge.runtime.observeOperation
import io.ktor.server.application.ApplicationCall
import kotlinx.coroutines.CancellationException

/** Executes one portable [PageUseCase] from a Ktor route. */
public class WogeKtorPageHandler<Input : Any> internal constructor(
    private val page: PageUseCase<Input>,
    private val input: KtorPageInput<Input>,
    private val contexts: KtorRequestContextFactory,
    private val observer: WogeObserver,
) {
    /** Decodes, executes and maps the page without an application-owned transport controller. */
    @Suppress("TooGenericExceptionCaught")
    public suspend fun handle(call: ApplicationCall) {
        val context = contexts.create(call)
        val observationContext = WogeObservationContext(requestTrace = context.trace)
        val pageRequest = PageRequest(input.decode(call), context)
        val result =
            try {
                observer.observeOperation(
                    operation = WogeOperation.PAGE_REQUEST,
                    context = observationContext,
                    successfulOutcome = { it.observationOutcome() },
                ) {
                    page.open(pageRequest)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                call.respondWogePreStreamFailure(failure)
                return
            }
        call.respondWogePage(result, observer, observationContext)
    }
}

package dev.woge.ktor

import dev.woge.host.FailurePages
import dev.woge.host.PageRequest
import dev.woge.host.PageUseCase
import dev.woge.host.RouteValueException
import dev.woge.host.WogeObservationContext
import dev.woge.host.WogeObserver
import dev.woge.host.WogeOperation
import dev.woge.host.failure
import dev.woge.host.withFailurePages
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
    private val failurePages: FailurePages,
) {
    /** Decodes, executes and maps the page without an application-owned transport controller. */
    @Suppress("TooGenericExceptionCaught")
    public suspend fun handle(call: ApplicationCall) {
        val context = contexts.create(call)
        val observationContext = WogeObservationContext(requestTrace = context.trace)
        val result =
            try {
                val pageRequest = PageRequest(input.decode(call), context)
                observer.observeOperation(
                    operation = WogeOperation.PAGE_REQUEST,
                    context = observationContext,
                    successfulOutcome = { it.observationOutcome() },
                ) {
                    page.open(pageRequest)
                }
            } catch (invalid: RouteValueException) {
                failure(invalid.category, context.correlationId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                call.respondWogePreStreamFailure(failure)
                return
            }
        call.respondWogePage(result.withFailurePages(failurePages), observer, observationContext)
    }
}

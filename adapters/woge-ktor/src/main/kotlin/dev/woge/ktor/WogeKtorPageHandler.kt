package dev.woge.ktor

import dev.woge.host.FailureCategory
import dev.woge.host.FailurePages
import dev.woge.host.FormDecodingException
import dev.woge.host.MUTATION_REQUEST_IDENTITY_HEADER
import dev.woge.host.MutationRequestIdentityException
import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.PageUseCase
import dev.woge.host.RouteValueException
import dev.woge.host.UnverifiedActionSecurityException
import dev.woge.host.UploadDecodingException
import dev.woge.host.WogeObservationContext
import dev.woge.host.WogeObserver
import dev.woge.host.WogeOperation
import dev.woge.host.failure
import dev.woge.host.requireActionSecurity
import dev.woge.host.withFailurePages
import dev.woge.host.withMutationRequestIdentity
import dev.woge.host.withUploadCleanup
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
    public suspend fun handle(call: ApplicationCall): Unit = handle(call, actionAccept = null)

    internal suspend fun handleAction(call: ApplicationCall) = handle(call, call.request.headers["Accept"].orEmpty())

    @Suppress("TooGenericExceptionCaught")
    private suspend fun handle(
        call: ApplicationCall,
        actionAccept: String?,
    ) {
        val context = contexts.create(call)
        val observationContext = WogeObservationContext(requestTrace = context.trace)
        val result =
            try {
                val actionContext =
                    if (actionAccept != null) {
                        context.requireActionSecurity()
                        context.withMutationRequestIdentity(
                            call.request.headers
                                .getAll(MUTATION_REQUEST_IDENTITY_HEADER)
                                .orEmpty(),
                        )
                    } else {
                        context
                    }
                val pageRequest = PageRequest(input.decode(call), actionContext)
                pageRequest.withUploadCleanup {
                    observer.observeOperation(
                        operation = WogeOperation.PAGE_REQUEST,
                        context = observationContext,
                        successfulOutcome = { it.observationOutcome() },
                    ) {
                        page.open(pageRequest)
                    }
                }
            } catch (invalid: RouteValueException) {
                failure(invalid.category, context.correlationId)
            } catch (invalid: FormDecodingException) {
                failure(invalid.category, context.correlationId)
            } catch (invalid: UploadDecodingException) {
                failure(invalid.category, context.correlationId)
            } catch (_: MutationRequestIdentityException) {
                failure(FailureCategory.BAD_REQUEST, context.correlationId)
            } catch (_: UnverifiedActionSecurityException) {
                failure(FailureCategory.FORBIDDEN, context.correlationId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                call.respondWogePreStreamFailure(failure)
                return
            }
        val accept =
            actionAccept ?: if (result is PageResult.RegionUpdates) call.request.headers["Accept"].orEmpty() else null
        call.respondWogePage(result.withFailurePages(failurePages), observer, observationContext, accept)
    }
}

package dev.woge.tck

import dev.woge.host.AuthenticationFacts
import dev.woge.host.CorrelationId
import dev.woge.host.CsrfVerification
import dev.woge.host.FailureCategory
import dev.woge.host.FormDecoder
import dev.woge.host.FormError
import dev.woge.host.FormErrors
import dev.woge.host.FormLimits
import dev.woge.host.FormValidation
import dev.woge.host.PageResult
import dev.woge.host.PageUseCase
import dev.woge.host.PrincipalFacts
import dev.woge.host.PrincipalId
import dev.woge.host.RequestContext
import dev.woge.host.RequestId
import dev.woge.host.RequestMethod
import dev.woge.host.RequestSecurity
import dev.woge.host.RequestTrace
import dev.woge.host.WogeAction
import dev.woge.host.actionValidationUpdates
import dev.woge.host.failure
import dev.woge.host.redirect
import dev.woge.html.applicationUrl
import kotlinx.serialization.Serializable

/** Command bound unchanged by every adapter in the action contract. */
@Serializable
public data class TckActionCommand(
    val value: String,
)

/** Small request-owned budgets make all adapter exhaustion paths observable over real HTTP. */
public val tckActionForm: FormDecoder<TckActionCommand> =
    FormDecoder(
        TckActionCommand.serializer(),
        FormLimits(bodyBytes = 128, fieldCount = 3, nameBytes = 16, valueBytes = 32),
    )

/** Generated executor receives host-translated identity and CSRF facts before domain authorization. */
@WogeAction("tck-submit")
public suspend fun tckSubmit(
    command: TckActionCommand,
    context: RequestContext,
): PageResult =
    if (authorized(context) && command.value in setOf("accepted", "update")) {
        redirect(applicationUrl("/woge-tck/action-complete"))
    } else {
        failure(FailureCategory.FORBIDDEN, context.correlationId)
    }

/** Field-error rendering shares the action's domain authorization rather than trusting parsed input. */
public val tckActionValidation: PageUseCase<FormValidation> =
    PageUseCase { request ->
        if (!authorized(request.context)) {
            failure(FailureCategory.FORBIDDEN, request.context.correlationId)
        } else {
            val errors =
                FormErrors(
                    request.input.values,
                    request.input.errors.map {
                        FormError(
                            if (it.field ==
                                tckValueField.name
                            ) {
                                tckValueField
                            } else {
                                null
                            },
                            "${it.field}: ${it.code}",
                        )
                    },
                )
            actionValidationUpdates(tckValidationDocument(errors), tckErrorSummary) {
                replace(ActionCommandFormRegion.target(actionPageIdentity()), errors)
            }
        }
    }

private fun authorized(context: RequestContext): Boolean {
    check(context.method == RequestMethod.POST && context.csrf == CsrfVerification.VERIFIED)
    val principal = (context.authentication as? AuthenticationFacts.Authenticated)?.principal
    return principal?.subject?.value == "tck-user"
}

/** Test-only ingress mapping: the harness simulates facts established by a host security integration. */
public fun tckActionContext(subject: String?): RequestContext =
    RequestContext(
        method = RequestMethod.POST,
        trace = RequestTrace(RequestId.of("tck-action"), CorrelationId.of("tck-action")),
        security =
            RequestSecurity(
                authentication =
                    subject?.let {
                        AuthenticationFacts.Authenticated(PrincipalFacts(PrincipalId.of(it)))
                    } ?: AuthenticationFacts.Anonymous,
                csrf = CsrfVerification.VERIFIED,
            ),
    )

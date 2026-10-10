package dev.woge.tck

import dev.woge.host.AuthenticationFacts
import dev.woge.host.CorrelationId
import dev.woge.host.CsrfVerification
import dev.woge.host.FailureCategory
import dev.woge.host.PageResult
import dev.woge.host.PrincipalFacts
import dev.woge.host.PrincipalId
import dev.woge.host.RequestContext
import dev.woge.host.RequestId
import dev.woge.host.RequestMethod
import dev.woge.host.RequestSecurity
import dev.woge.host.RequestTrace
import dev.woge.host.WogeAction
import dev.woge.host.failure
import dev.woge.host.redirect
import dev.woge.html.applicationUrl

/** Command bound unchanged by every adapter in the action contract. */
public data class TckActionCommand(
    val value: String,
)

/** Generated executor receives host-translated identity and CSRF facts before domain authorization. */
@WogeAction("tck-submit")
public suspend fun tckSubmit(
    command: TckActionCommand,
    context: RequestContext,
): PageResult {
    val principal = (context.authentication as? AuthenticationFacts.Authenticated)?.principal
    check(context.method == RequestMethod.POST && context.csrf == CsrfVerification.VERIFIED)
    return if (principal?.subject?.value == "tck-user" && command.value == "accepted") {
        redirect(applicationUrl("/woge-tck/action-complete"))
    } else {
        failure(FailureCategory.FORBIDDEN, context.correlationId)
    }
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

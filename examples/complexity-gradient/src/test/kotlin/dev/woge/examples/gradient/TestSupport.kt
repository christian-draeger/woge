package dev.woge.examples.gradient

import dev.woge.host.CorrelationId
import dev.woge.host.CsrfVerification
import dev.woge.host.PageResult
import dev.woge.host.RequestContext
import dev.woge.host.RequestId
import dev.woge.host.RequestMethod
import dev.woge.host.RequestSecurity
import dev.woge.host.RequestTrace
import dev.woge.html.HtmlSink

private val trace = RequestTrace(RequestId.of("gradient"), CorrelationId.of("gradient"))

val getContext = RequestContext(RequestMethod.GET, trace)
val postContext =
    RequestContext(RequestMethod.POST, trace, security = RequestSecurity(csrf = CsrfVerification.VERIFIED))

suspend fun PageResult.html(): String {
    val out = StringBuilder()
    (this as PageResult.Document).frames.collect { it.writeTo(HtmlSink { out.append(it) }) }
    return out.toString()
}

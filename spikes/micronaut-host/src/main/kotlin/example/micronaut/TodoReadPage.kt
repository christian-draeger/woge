package example.micronaut

import dev.woge.host.CorrelationId
import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.RequestContext
import dev.woge.host.RequestId
import dev.woge.host.RequestMethod
import dev.woge.host.RequestTrace
import dev.woge.host.RouteParameters
import dev.woge.host.writeTo
import dev.woge.html.HtmlSink
import example.todo.TodosPage
import example.todo.TodosRoute
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import kotlinx.coroutines.runBlocking
import java.util.UUID

internal object TodoReadPage {
    private val page = TodosPage()

    @JvmStatic
    fun render(request: HttpRequest<*>): HttpResponse<ByteArray> = runBlocking {
        val input = TodosRoute.decode(object : RouteParameters {
            override fun path(name: String): String? = null

            override fun query(name: String): String? = request.parameters[name]
        })
        val id = UUID.randomUUID().toString()
        val context = RequestContext(
            method = RequestMethod.of(request.method.name),
            trace = RequestTrace(RequestId.of(id), CorrelationId.of(id)),
        )
        val result = page.open(PageRequest(input, context))
        val response = HttpResponse.status<ByteArray>(HttpStatus.valueOf(result.metadata.status.code))
        when (result) {
            is PageResult.Document -> {
                val html = StringBuilder()
                result.writeTo(HtmlSink { html.append(it) })
                response.contentType(requireNotNull(result.metadata.contentType).value)
                    .body(html.toString().toByteArray(Charsets.UTF_8))
            }
            is PageResult.Failure -> response
            else -> error("Unexpected result from the read-only Todo page")
        }
    }
}

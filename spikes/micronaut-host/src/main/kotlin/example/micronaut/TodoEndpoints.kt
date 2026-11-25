package example.micronaut

import dev.woge.host.ACTION_VALIDATION_HEADER
import dev.woge.host.AuthenticationFacts
import dev.woge.host.Capability
import dev.woge.host.CorrelationId
import dev.woge.host.CsrfVerification
import dev.woge.host.FormDecodingException
import dev.woge.host.MUTATION_REQUEST_IDENTITY_HEADER
import dev.woge.host.MutationRequestIdentityException
import dev.woge.host.PageRequest
import dev.woge.host.PageResult
import dev.woge.host.PageUseCase
import dev.woge.host.PrincipalFacts
import dev.woge.host.PrincipalId
import dev.woge.host.RenderSessions
import dev.woge.host.RequestContext
import dev.woge.host.RequestHeaders
import dev.woge.host.RequestId
import dev.woge.host.RequestMethod
import dev.woge.host.RequestSecurity
import dev.woge.host.RequestTrace
import dev.woge.host.ResponseMetadata
import dev.woge.host.RouteParameters
import dev.woge.host.SameSite
import dev.woge.host.UnverifiedActionSecurityException
import dev.woge.host.acceptsActionPatches
import dev.woge.host.getOrThrow
import dev.woge.host.httpHeader
import dev.woge.host.page
import dev.woge.host.requestsUnsupportedActionPatchVersion
import dev.woge.host.requireActionSecurity
import dev.woge.host.sameOriginCsrf
import dev.woge.host.withMutationRequestIdentity
import dev.woge.host.writeTo
import dev.woge.html.HtmlSink
import dev.woge.protocol.PatchStreamV1
import dev.woge.runtime.encodeActionPatchStream
import example.todo.AddTodo
import example.todo.AddTodoAction
import example.todo.TodosPage
import example.todo.TodosRoute
import example.todo.addForm
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.ServerHttpRequest
import io.micronaut.http.cookie.Cookie
import io.micronaut.http.cookie.SameSite as MicronautSameSite
import io.micronaut.http.exceptions.ContentLengthExceededException
import io.micronaut.security.authentication.Authentication
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.util.UUID

internal object TodoEndpoints {
    private val sessions = RenderSessions()
    private val page = sessions.page(TodosPage())
    private val addTodo = sessions.page(PageUseCase(AddTodoAction::execute))
    private val writeCapability = Capability.of("todo:write")
    private val renderHeaders = listOf("Woge-Page-Epoch", "Woge-Region-Revisions", "Woge-Interaction")

    @JvmStatic
    fun render(request: HttpRequest<*>, authentication: Authentication?): HttpResponse<ByteArray> = runBlocking {
        val input = TodosRoute.decode(object : RouteParameters {
            override fun path(name: String): String? = null

            override fun query(name: String): String? = request.parameters[name]
        })
        response(page.open(PageRequest(input, requestContext(request, authentication))))
    }

    fun requestContext(request: HttpRequest<*>, authentication: Authentication?): RequestContext =
        request.context(
            authentication?.let { AuthenticationFacts.Authenticated(it.toPrincipal()) }
                ?: AuthenticationFacts.Anonymous,
        )

    @JvmStatic
    fun add(request: HttpRequest<*>, authentication: Authentication): HttpResponse<ByteArray> = runBlocking {
        val serverRequest = request as? ServerHttpRequest<*>
            ?: return@runBlocking rejected(HttpStatus.INTERNAL_SERVER_ERROR)
        val local = serverRequest.serverAddress
        if (local.address?.isLoopbackAddress != true) return@runBlocking rejected(HttpStatus.FORBIDDEN)
        val scheme = if (request.isSecure) "https" else "http"
        val origin = URI(scheme, null, local.address.hostAddress, local.port, null, null, null)
        val csrf = sameOriginCsrf(
            RequestMethod.POST,
            request.joinedHeader("Origin"),
            request.joinedHeader("Sec-Fetch-Site"),
            origin.toASCIIString(),
        )
        val principal = authentication.toPrincipal()
        if (!principal.has(writeCapability)) return@runBlocking rejected(HttpStatus.FORBIDDEN)
        val context = request.context(AuthenticationFacts.Authenticated(principal), csrf)

        val accept = request.joinedHeader("Accept")
        try {
            context.requireActionSecurity()
            if (requestsUnsupportedActionPatchVersion(accept)) {
                return@runBlocking rejected(HttpStatus.NOT_ACCEPTABLE).header("Woge-Protocol-Error", "unsupported-version")
            }
            val actionContext = context.withMutationRequestIdentity(request.headers.getAll(MUTATION_REQUEST_IDENTITY_HEADER))
            val command = serverRequest.addCommand()
            response(addTodo.open(PageRequest(command, actionContext)), accept)
                .header("Vary", "Accept")
                .header("Cache-Control", "no-store, no-transform")
        } catch (_: UnverifiedActionSecurityException) {
            rejected(HttpStatus.FORBIDDEN)
        } catch (_: MutationRequestIdentityException) {
            rejected(HttpStatus.BAD_REQUEST)
        } catch (invalid: FormDecodingException) {
            rejected(HttpStatus.valueOf(invalid.category.status.code))
        } catch (readFailure: IOException) {
            // Micronaut wraps ByteBody stream failures in an IOException.
            if (readFailure.cause !is ContentLengthExceededException) throw readFailure
            rejected(HttpStatus.REQUEST_ENTITY_TOO_LARGE)
        }
    }

    private fun ServerHttpRequest<*>.addCommand(): AddTodo {
        addForm.requireContentType(joinedHeader("Content-Type"))
        val form = addForm.body()
        byteBody().toInputStream().use { input ->
            val buffer = ByteArray(4096)
            var count = input.read(buffer)
            while (count >= 0 && form.accept(buffer, length = count)) count = input.read(buffer)
        }
        return addForm.decode(form).getOrThrow()
    }

    private fun HttpRequest<*>.context(
        authentication: AuthenticationFacts,
        csrf: CsrfVerification = CsrfVerification.NOT_REQUIRED,
    ): RequestContext {
        val id = UUID.randomUUID().toString()
        val received = renderHeaders.flatMap { name -> headers.getAll(name).map { value -> httpHeader(name, value) } }
        return RequestContext(
            method = RequestMethod.of(method.name),
            trace = RequestTrace(RequestId.of(id), CorrelationId.of(id)),
            headers = RequestHeaders.of(received),
            security = RequestSecurity(authentication, csrf),
        )
    }

    private fun Authentication.toPrincipal(): PrincipalFacts =
        PrincipalFacts(PrincipalId.of(name), roles.map(Capability::of))

    private fun HttpRequest<*>.joinedHeader(name: String): String? =
        headers.getAll(name).takeIf { it.isNotEmpty() }?.joinToString(",")

    private fun rejected(status: HttpStatus): MutableHttpResponse<ByteArray> =
        HttpResponse.status<ByteArray>(status).header("Cache-Control", "no-store")

    private suspend fun response(
        result: PageResult,
        accept: String? = null,
    ): MutableHttpResponse<ByteArray> {
        val selected =
            if (result is PageResult.RegionUpdates && !acceptsActionPatches(accept)) result.nativeResult else result
        val reply = selected.metadata.micronautResponse<ByteArray>()
        return when (selected) {
            is PageResult.Document -> {
                val html = StringBuilder()
                selected.writeTo(HtmlSink { html.append(it) })
                reply.body(html.toString().toByteArray(Charsets.UTF_8))
            }
            is PageResult.Redirect -> reply.header("Location", selected.location.value)
            is PageResult.RegionUpdates -> {
                val bytes = ByteArrayOutputStream()
                selected.encodeActionPatchStream().collect { bytes.write(it.bytes) }
                selected.focusSummary?.let { reply.header(ACTION_VALIDATION_HEADER, it.value) }
                reply.contentType(PatchStreamV1.MEDIA_TYPE).body(bytes.toByteArray())
            }
            is PageResult.Failure, is PageResult.NotModified -> reply
        }
    }
}

internal fun <Body> ResponseMetadata.micronautResponse(): MutableHttpResponse<Body> {
    val response = HttpResponse.status<Body>(HttpStatus.valueOf(status.code))
    contentType?.let { response.contentType(it.value) }
    headers.forEach { response.header(it.name.value, it.value.value) }
    for (metadata in cookies) {
        val site = when (metadata.sameSite) {
            SameSite.STRICT -> MicronautSameSite.Strict
            SameSite.LAX -> MicronautSameSite.Lax
            SameSite.NONE -> MicronautSameSite.None
        }
        val cookie = Cookie.of(metadata.name.value, metadata.value.value)
            .path(metadata.path.value)
            .secure(metadata.secure)
            .httpOnly(metadata.httpOnly)
            .sameSite(site)
        metadata.maxAgeSeconds?.let(cookie::maxAge)
        response.cookie(cookie)
    }
    return response
}

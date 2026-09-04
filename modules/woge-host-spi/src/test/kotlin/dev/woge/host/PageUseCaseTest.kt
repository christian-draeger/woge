package dev.woge.host

import dev.woge.html.BufferedHtmlSink
import dev.woge.html.HtmlSink
import dev.woge.html.applicationUrl
import dev.woge.html.externalUrl
import dev.woge.protocol.htmlFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

class PageUseCaseTest {
    @Test
    fun `in-memory host executes one portable typed page`() =
        runBlocking {
            val useCase =
                PageUseCase<String> { request ->
                    streamingHtmlPage(
                        flow {
                            emit(
                                htmlFrame {
                                    element("h1") { text("Hello ${request.input}") }
                                },
                            )
                            emit(
                                htmlFrame {
                                    element("p") { text("Rendered without a host framework") }
                                },
                            )
                        },
                    )
                }
            val request = PageRequest("Web <developer>", requestContext())

            val response = InMemoryHost().execute(useCase, request)

            assertEquals(200, response.status)
            assertEquals("text/html; charset=UTF-8", response.contentType)
            assertEquals(
                "<h1>Hello Web &lt;developer&gt;</h1><p>Rendered without a host framework</p>",
                response.body,
            )
        }

    @Test
    fun `authentication facts never imply domain authorization`() =
        runBlocking {
            val editCapability = Capability.of("projects:edit")
            val useCase =
                PageUseCase<ProjectRequest> { request ->
                    val principal =
                        (request.context.authentication as? AuthenticationFacts.Authenticated)?.principal
                    val ownsProject = principal?.subject?.value == request.input.owner
                    if (principal?.has(editCapability) == true && ownsProject) {
                        htmlPage { text("allowed") }
                    } else {
                        failure(FailureCategory.FORBIDDEN, request.context.correlationId)
                    }
                }
            val authenticatedButNotOwner =
                requestContext(
                    authentication =
                        AuthenticationFacts.Authenticated(
                            PrincipalFacts(PrincipalId.of("alice"), listOf(editCapability)),
                        ),
                    csrf = CsrfVerification.VERIFIED,
                )

            val result = useCase.open(PageRequest(ProjectRequest(owner = "bob"), authenticatedButNotOwner))

            val denied = assertInstanceOf(PageResult.Failure::class.java, result)
            assertEquals(FailureCategory.FORBIDDEN, denied.failure.category)
            assertEquals(CsrfVerification.VERIFIED, authenticatedButNotOwner.csrf)
        }

    @Test
    fun `same-application redirect is the default and external redirect needs policy`() {
        val local = redirect(applicationUrl("/projects/42"))

        assertEquals(303, local.metadata.status.code)
        assertEquals("/projects/42", local.location.value)
        assertEquals(null, local.metadata.contentType)

        val external = externalUrl("https://accounts.example.test/login")
        assertThrows(IllegalArgumentException::class.java) {
            externalRedirect(external, ExternalRedirectPolicy { false })
        }

        val allowed =
            externalRedirect(
                external,
                ExternalRedirectPolicy { it.value.startsWith("https://accounts.example.test/") },
            )
        assertEquals(external.value, allowed.location.value)
        assertThrows(IllegalArgumentException::class.java) {
            redirect(applicationUrl("/ok"), status = ResponseStatus.OK)
        }
        assertThrows(IllegalArgumentException::class.java) {
            redirect(applicationUrl("/not-modified"), status = ResponseStatus.of(304))
        }
        assertThrows(IllegalArgumentException::class.java) {
            htmlPage(ResponseMetadata(status = ResponseStatus.of(204))) { text("not allowed") }
        }
    }

    @Test
    fun `downstream failure reaches the cold frame flow unchanged`() {
        val writeFailure = IOException("connection closed")
        val completion = AtomicReference<Throwable?>()
        val document =
            streamingHtmlPage(
                flow {
                    emit(htmlFrame { text("first") })
                    emit(htmlFrame { text("second") })
                }.onCompletion { cause -> completion.set(cause) },
            )

        val thrown =
            assertThrows(IOException::class.java) {
                runBlocking {
                    document.writeTo(HtmlSink { throw writeFailure })
                }
            }

        assertSame(writeFailure, thrown)
        assertSame(writeFailure, completion.get())
    }

    @Test
    fun `request cancellation cancels frame production`() =
        runBlocking {
            val waiting = CompletableDeferred<Unit>()
            val completion = AtomicReference<Throwable?>()
            val document =
                streamingHtmlPage(
                    flow {
                        emit(htmlFrame { text("visible") })
                        waiting.complete(Unit)
                        awaitCancellation()
                    }.onCompletion { cause -> completion.set(cause) },
                )
            val sink = BufferedHtmlSink()
            val requestJob = launch { document.writeTo(sink) }

            waiting.await()
            requestJob.cancel(CancellationException("client disconnected"))
            requestJob.cancelAndJoin()

            assertEquals("visible", sink.content())
            assertTrue(completion.get() is CancellationException)
        }

    @Test
    fun `controlled failure exposes no raw request or exception detail`() {
        val secret = "password=hunter2"
        val result = failure(FailureCategory.INTERNAL, CorrelationId.of("safe-trace"))
        val publicDiagnostic = result.failure.toString() + result.metadata.toString()

        assertFalse(publicDiagnostic.contains(secret))
        assertFalse(publicDiagnostic.contains("stack"))
        assertTrue(publicDiagnostic.contains("safe-trace"))
    }
}

private data class ProjectRequest(
    val owner: String,
)

private data class CapturedResponse(
    val status: Int,
    val contentType: String?,
    val body: String,
)

private class InMemoryHost {
    suspend fun <Input : Any> execute(
        useCase: PageUseCase<Input>,
        request: PageRequest<Input>,
    ): CapturedResponse {
        val result = useCase.open(request)
        val sink = BufferedHtmlSink()
        if (result is PageResult.Document) {
            result.writeTo(sink, maxChunkChars = 16)
        }
        return CapturedResponse(
            status = result.metadata.status.code,
            contentType = result.metadata.contentType?.value,
            body = sink.content(),
        )
    }
}

private fun requestContext(
    authentication: AuthenticationFacts = AuthenticationFacts.Anonymous,
    csrf: CsrfVerification = CsrfVerification.NOT_REQUIRED,
): RequestContext =
    RequestContext(
        method = RequestMethod.GET,
        trace = RequestTrace(RequestId.of("request-1"), CorrelationId.of("trace-1")),
        language = LanguageTag.of("en-US"),
        security = RequestSecurity(authentication, csrf),
    )

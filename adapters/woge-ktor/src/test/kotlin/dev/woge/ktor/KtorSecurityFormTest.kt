package dev.woge.ktor

import dev.woge.host.ActionExecutor
import dev.woge.host.FormDecodingException
import dev.woge.host.FormSubmission
import dev.woge.host.withFormValidation
import dev.woge.tck.PreparedSecurityForm
import dev.woge.tck.SecurityFormContract
import dev.woge.tck.TckActionCommand
import dev.woge.tck.TckSubmitAction
import dev.woge.tck.tckActionContext
import dev.woge.tck.tckActionValidation
import dev.woge.tck.tckSecurityAction
import dev.woge.tck.tckSecurityForm
import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.net.URI
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Test-only session token policy; applications use their configured authentication/session integration. */
class KtorSecurityFormTest {
    @Test
    fun `Ktor checks native and enhanced tokens before independent domain authorization`() {
        val tokens = ConcurrentHashMap<String, String>()
        val action = tckSecurityAction().withFormValidation(tckActionValidation)
        val server =
            embeddedServer(Netty, host = "127.0.0.1", port = 0) {
                routing {
                    get("/expire-tck-session") {
                        call.request.cookies["tck-session"]?.let(tokens::remove)
                        call.respond(HttpStatusCode.OK)
                    }
                    get("/csrf") {
                        if (subject(call) == null) {
                            call.respond(HttpStatusCode.Unauthorized)
                        } else {
                            val session = call.request.cookies["tck-session"] ?: UUID.randomUUID().toString()
                            val token = tokens.computeIfAbsent(session) { UUID.randomUUID().toString() }
                            call.response.cookies.append(Cookie("tck-session", session, path = "/", httpOnly = true))
                            call.respondText(token)
                        }
                    }
                    post(TckSubmitAction.path) {
                        verifySubmission(call, tokens, action)
                    }
                }
            }.start(wait = false)
        try {
            val port =
                runBlocking {
                    server.engine
                        .resolvedConnectors()
                        .single()
                        .port
                }
            SecurityFormContract().verify(URI.create("http://127.0.0.1:$port"))
        } finally {
            server.stop(1_000, 1_000)
        }
    }
}

@Suppress("ReturnCount")
private suspend fun verifySubmission(
    call: ApplicationCall,
    tokens: Map<String, String>,
    action: ActionExecutor<FormSubmission<TckActionCommand>>,
) {
    val user = subject(call)
    if (user == null) {
        call.respond(HttpStatusCode.Unauthorized)
        return
    }
    val prepared =
        try {
            PreparedSecurityForm(tckSecurityForm.ktorSubmission().decode(call))
        } catch (invalid: FormDecodingException) {
            call.respond(HttpStatusCode.fromValue(invalid.category.status.code))
            return
        }
    val headers =
        call.request.headers
            .getAll("X-CSRF-TOKEN")
            .orEmpty()
    val token = if (headers.isEmpty()) prepared.csrfToken else headers.singleOrNull()
    val expected = call.request.cookies["tck-session"]?.let(tokens::get)
    if (token == null || expected == null || !MessageDigest.isEqual(token.toByteArray(), expected.toByteArray())) {
        call.respond(HttpStatusCode.Forbidden)
        return
    }
    WogeKtorHandlers()
        .action(
            action,
            KtorPageInput { prepared.submission },
            KtorRequestContextFactory { tckActionContext(user) },
        ).handle(call)
}

private fun subject(call: ApplicationCall): String? {
    val header = call.request.headers["Authorization"] ?: return null
    return listOf("tck-user", "other-user").firstOrNull { user ->
        header == "Basic " + Base64.getEncoder().encodeToString("$user:fixture-password".toByteArray())
    }
}

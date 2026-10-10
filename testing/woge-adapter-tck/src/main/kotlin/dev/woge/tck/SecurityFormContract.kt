package dev.woge.tck

import dev.woge.host.ResponseStatus
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64

/** Real Security harnesses expose a token GET and use two authenticated domain identities. */
public class SecurityFormContract {
    public fun verify(origin: URI) {
        val client = HttpClient.newBuilder().cookieHandler(CookieManager(null, CookiePolicy.ACCEPT_ALL)).build()
        val token =
            client.send(
                HttpRequest
                    .newBuilder(origin.resolve("/csrf"))
                    .header("Authorization", basic("tck-user"))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        check(token.statusCode() == ResponseStatus.OK.code && token.body().isNotEmpty()) {
            "Authenticated CSRF token was not issued"
        }
        expectStatus(ResponseStatus.FORBIDDEN, post(client, origin, "tck-user", null, "value=accepted"))
        expectStatus(ResponseStatus.FORBIDDEN, post(client, origin, "tck-user", "invalid", "value=accepted"))
        val accepted = post(client, origin, "tck-user", token.body(), "value=accepted")
        expectStatus(ResponseStatus.SEE_OTHER, accepted)
        check(accepted.headers().firstValue("Location").orElseThrow() == "/woge-tck/action-complete") {
            "Authorized mutation did not redirect to the canonical page"
        }
        expectStatus(ResponseStatus.FORBIDDEN, post(client, origin, "other-user", token.body(), "value=accepted"))
        expectStatus(ResponseStatus.BAD_REQUEST, post(client, origin, "tck-user", token.body(), "value=a&value=b"))
        expectStatus(ResponseStatus.UNAUTHORIZED, post(client, origin, null, token.body(), "value=accepted"))
        verifyNative(client, origin, token.body())
        verifyReplay(client, origin, token.body())
        verifyExpiredSession(client, origin, token.body())
    }

    private fun verifyExpiredSession(
        client: HttpClient,
        origin: URI,
        token: String,
    ) {
        val expired =
            client.send(
                HttpRequest
                    .newBuilder(origin.resolve("/expire-tck-session"))
                    .header("Authorization", basic("tck-user"))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.discarding(),
            )
        expectStatus(ResponseStatus.OK, expired)
        expectStatus(ResponseStatus.FORBIDDEN, post(client, origin, "tck-user", token, "value=accepted"))
        val field = "_csrf=${java.net.URLEncoder.encode(token, Charsets.UTF_8)}"
        expectStatus(ResponseStatus.FORBIDDEN, post(client, origin, "tck-user", null, "value=accepted&$field"))
    }

    private fun verifyReplay(
        client: HttpClient,
        origin: URI,
        token: String,
    ) {
        var count = 0
        for (enhanced in listOf(false, true)) {
            for ((command, state, changes) in listOf(
                Triple("replay-completed", "COMPLETED", 1),
                Triple("replay-rejected", "REJECTED", 0),
                Triple("replay-ambiguous", "AMBIGUOUS", 1),
                Triple("replay-in-progress", "IN_PROGRESS", 0),
            )) {
                val identity =
                    java.util.UUID
                        .randomUUID()
                        .toString()
                val field = "_csrf=${java.net.URLEncoder.encode(token, Charsets.UTF_8)}"
                val body = "value=$command" + if (enhanced) "" else "&$field"
                count += changes
                repeat(2) {
                    val response =
                        post(client, origin, "tck-user", if (enhanced) token else null, body, identity, enhanced)
                    expectStatus(if (enhanced) ResponseStatus.OK else ResponseStatus.SEE_OTHER, response)
                    check(response.headers().firstValue("Woge-Test-Mutation-State").orElseThrow() == state)
                    check(response.headers().firstValue("Woge-Test-Mutation-Count").orElseThrow() == count.toString())
                }
                expectStatus(
                    ResponseStatus.FORBIDDEN,
                    post(client, origin, "other-user", token, body, identity, enhanced),
                )
                expectStatus(
                    ResponseStatus.FORBIDDEN,
                    post(client, origin, "tck-user", null, "value=$command", identity, enhanced),
                )
            }
        }
    }

    private fun verifyNative(
        client: HttpClient,
        origin: URI,
        token: String,
    ) {
        val field = "_csrf=${java.net.URLEncoder.encode(token, Charsets.UTF_8)}"
        expectStatus(ResponseStatus.SEE_OTHER, post(client, origin, "tck-user", null, "value=accepted&$field"))
        expectStatus(ResponseStatus.FORBIDDEN, post(client, origin, "tck-user", null, "value=accepted&_csrf=invalid"))
        expectStatus(ResponseStatus.FORBIDDEN, post(client, origin, "tck-user", null, "value=accepted&$field&$field"))
        expectStatus(ResponseStatus.FORBIDDEN, post(client, origin, "other-user", null, "value=accepted&$field"))
        expectStatus(ResponseStatus.BAD_REQUEST, post(client, origin, "tck-user", null, "value=a&value=b&$field"))
        expectStatus(ResponseStatus.UNAUTHORIZED, post(client, origin, null, null, "value=accepted&$field"))
        expectStatus(
            ResponseStatus.PAYLOAD_TOO_LARGE,
            post(client, origin, "tck-user", null, "value=${"a".repeat(tckSecurityForm.limits.valueBytes + 1)}&$field"),
        )
        expectStatus(ResponseStatus.BAD_REQUEST, post(client, origin, "tck-user", null, "value=%GG&$field"))
        expectStatus(
            ResponseStatus.FORBIDDEN,
            client.send(
                HttpRequest
                    .newBuilder(origin.resolve("${TckSubmitAction.path}?$field"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Authorization", basic("tck-user"))
                    .POST(HttpRequest.BodyPublishers.ofString("value=accepted"))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            ),
        )
    }

    @Suppress("LongParameterList")
    private fun post(
        client: HttpClient,
        origin: URI,
        user: String?,
        token: String?,
        body: String,
        identity: String? = null,
        enhanced: Boolean = false,
    ): HttpResponse<String> =
        client.send(
            HttpRequest
                .newBuilder(origin.resolve(TckSubmitAction.path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .apply {
                    user?.let { header("Authorization", basic(it)) }
                    token?.let { header("X-CSRF-TOKEN", it) }
                    identity?.let { header("Woge-Request-Identity", it) }
                    if (enhanced) header("Accept", dev.woge.protocol.PatchStreamV1.MEDIA_TYPE)
                }.POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun expectStatus(
        status: ResponseStatus,
        response: HttpResponse<*>,
    ) {
        check(response.statusCode() == status.code) {
            "Expected Security form status ${status.code}, got ${response.statusCode()}"
        }
    }
}

private fun basic(user: String): String =
    "Basic " + Base64.getEncoder().encodeToString("$user:fixture-password".toByteArray())

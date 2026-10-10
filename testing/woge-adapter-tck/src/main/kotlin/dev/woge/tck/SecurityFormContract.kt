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
    }

    private fun post(
        client: HttpClient,
        origin: URI,
        user: String?,
        token: String?,
        body: String,
    ): HttpResponse<String> =
        client.send(
            HttpRequest
                .newBuilder(origin.resolve(TckSubmitAction.path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .apply {
                    user?.let { header("Authorization", basic(it)) }
                    token?.let { header("X-CSRF-TOKEN", it) }
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

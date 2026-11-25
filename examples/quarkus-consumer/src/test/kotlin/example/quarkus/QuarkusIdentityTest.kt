package example.quarkus

import io.quarkus.test.common.http.TestHTTPResource
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.QuarkusTestProfile
import io.quarkus.test.junit.TestProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64

@QuarkusTest
@TestProfile(LocalIdentityFixture::class)
class QuarkusIdentityTest {
    @TestHTTPResource
    lateinit var origin: URI

    @Test
    fun `host identity survives worker suspension and other identities remain denied`() {
        HttpClient.newHttpClient().use { client ->
            val anonymous = HttpRequest.newBuilder(origin.resolve("/identity")).timeout(Duration.ofSeconds(5))
            assertEquals(403, client.send(anonymous.build(), HttpResponse.BodyHandlers.discarding()).statusCode())
            val forbidden = client.send(identity("visitor"), HttpResponse.BodyHandlers.discarding())
            assertEquals(403, forbidden.statusCode())
            val allowed = client.send(identity("checker"), HttpResponse.BodyHandlers.ofString())
            assertEquals(200, allowed.statusCode())
            assertTrue("Signed in as checker" in allowed.body())
        }
    }

    private fun identity(user: String): HttpRequest {
        val value = Base64.getEncoder().encodeToString("$user:test-only".toByteArray())
        return HttpRequest.newBuilder(origin.resolve("/identity"))
            .timeout(Duration.ofSeconds(5)).header("Authorization", "Basic $value").build()
    }
}

class LocalIdentityFixture : QuarkusTestProfile {
    override fun getConfigOverrides(): Map<String, String> =
        mapOf(
            "quarkus.http.auth.basic" to "true",
            "quarkus.security.users.embedded.enabled" to "true",
            "quarkus.security.users.embedded.plain-text" to "true",
            "quarkus.security.users.embedded.users.checker" to "test-only",
            "quarkus.security.users.embedded.roles.checker" to "reader",
            "quarkus.security.users.embedded.users.visitor" to "test-only",
            "quarkus.security.users.embedded.roles.visitor" to "visitor",
        )
}

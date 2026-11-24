package org.springframework.samples.petclinic.woge

import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.boot.SpringApplication
import org.springframework.boot.web.server.context.ConfigurableWebServerApplicationContext
import org.springframework.samples.petclinic.PetClinicApplication
import org.springframework.samples.petclinic.owner.OwnerRepository
import java.net.CookieManager
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import java.time.LocalDate

class ClinicAdoptionTest {
    @Test
    fun `secured native visits survive disabling the Woge routes`(@TempDir database: Path) {
        val databaseUrl = "jdbc:h2:file:${database.resolve("petclinic").toAbsolutePath()}"
        val ownerPath = "/owners/1"
        val visitPath = "$ownerPath/pets/1/visits/new"
        val wogeVisitPath = "/woge$visitPath"
        val visit = "Visit booked on the Woge page"
        val futureDate = LocalDate.now().plusDays(2).toString()

        startClinic(databaseUrl, wogeEnabled = true, initMode = "always").use { app ->
            val client = ClinicClient(app)
            assertLoginRedirect(client.get("/woge/owners"))
            client.login()

            val search = client.get("/woge/owners?lastName=Franklin")
            assertEquals(200, search.statusCode())
            assertTrue(Jsoup.parse(search.body()).selectFirst("a[href=/woge/owners/1]")!!.text().contains("Franklin"))
            assertTrue(client.get(ownerPath).body().contains("Franklin"))
            assertTrue(client.get("/api/reference$ownerPath", accept = "application/json").body().contains("Franklin"))

            val owner = client.get("/woge$ownerPath")
            val formPath = Jsoup.parse(owner.body()).selectFirst("a[href*=/visits/new]")!!.attr("href")
            assertEquals(wogeVisitPath, formPath)
            assertEquals(404, client.get("/woge/owners/1/pets/2/visits/new").statusCode())
            assertEquals(404, client.get("/woge/owners/9999/pets/1/visits/new").statusCode())
            val formPage = client.get(formPath)
            assertEquals(200, formPage.statusCode())
            val form = requireNotNull(Jsoup.parse(formPage.body()).selectFirst("form"))
            assertEquals(visitPath, form.attr("action"))
            val token = requireNotNull(form.selectFirst("input[name=_csrf]")).attr("value")

            fun submit(date: String, description: String, csrf: String? = token): HttpResponse<String> =
                client.postForm(visitPath, buildMap {
                    put("date", date)
                    put("description", description)
                    if (csrf != null) put("_csrf", csrf)
                })

            assertEquals(403, submit(futureDate, visit, null).statusCode())
            assertEquals(403, submit(futureDate, visit, "invalid-token").statusCode())
            val blank = submit(futureDate, "")
            assertEquals(200, blank.statusCode())
            assertTrue(Jsoup.parse(blank.body()).select(".has-error input[name=description]").isNotEmpty())
            val past = submit(LocalDate.now().minusDays(1).toString(), visit)
            assertEquals(200, past.statusCode())
            assertEquals(
                "Visit date must be in the future",
                Jsoup.parse(past.body()).select(".has-error .help-inline").text(),
            )
            val malformed = submit("not-a-date", visit)
            assertEquals(200, malformed.statusCode())
            assertTrue(Jsoup.parse(malformed.body()).select(".has-error input[name=date]").isNotEmpty())
            assertEquals(0, visitCount(app, visit))

            val saved = submit(futureDate, visit)
            assertEquals(302, saved.statusCode())
            assertEquals(ownerPath, URI.create(saved.headers().firstValue("location").orElseThrow()).path)
            assertTrue(client.get(ownerPath).body().contains(visit))
            assertTrue(client.get("/woge$ownerPath").body().contains(visit))
            assertEquals(1, visitCount(app, visit))

            assertEquals(302, client.postForm("/logout", mapOf("_csrf" to token)).statusCode())
            assertLoginRedirect(client.get(wogeVisitPath))
            assertLoginRedirect(submit(futureDate, visit))
            client.login()
            assertEquals(403, submit(futureDate, visit).statusCode())
            assertEquals(1, visitCount(app, visit))
        }

        startClinic(databaseUrl, wogeEnabled = false, initMode = "never").use { app ->
            val client = ClinicClient(app)
            assertLoginRedirect(client.get(ownerPath))
            assertLoginRedirect(client.get("/api/reference$ownerPath"))
            client.login()

            assertEquals(404, client.get("/woge/owners").statusCode())
            assertEquals(404, client.get("/woge$ownerPath").statusCode())
            assertEquals(404, client.get(wogeVisitPath).statusCode())
            val owner = client.get(ownerPath)
            assertEquals(200, owner.statusCode())
            assertTrue(owner.body().contains(visit))
            val json = client.get("/api/reference$ownerPath", accept = "application/json")
            assertEquals(200, json.statusCode())
            assertTrue(json.body().contains("Franklin"))

            val nativeForm = client.get(visitPath)
            assertEquals(200, nativeForm.statusCode())
            val form = requireNotNull(Jsoup.parse(nativeForm.body()).selectFirst("form:has(input[name=description])"))
            assertEquals(visitPath, form.attr("action"))
            val token = requireNotNull(form.selectFirst("input[name=_csrf]")).attr("value")
            val subsequentVisit = "Visit booked on the Thymeleaf page after rollback"
            val fields = mapOf("date" to futureDate, "description" to subsequentVisit)
            assertEquals(403, client.postForm(visitPath, fields).statusCode())
            assertEquals(302, client.postForm(visitPath, fields + ("_csrf" to token)).statusCode())
            assertTrue(client.get(ownerPath).body().contains(subsequentVisit))
            assertEquals(1, visitCount(app, visit))
            assertEquals(1, visitCount(app, subsequentVisit))
        }
    }

    private fun startClinic(
        databaseUrl: String,
        wogeEnabled: Boolean,
        initMode: String,
    ): ConfigurableWebServerApplicationContext =
        SpringApplication.run(
            PetClinicApplication::class.java,
            "--server.address=127.0.0.1",
            "--server.port=0",
            "--spring.security.user.name=reference",
            "--spring.security.user.password=local-test-only",
            "--spring.datasource.url=$databaseUrl",
            "--spring.sql.init.mode=$initMode",
            "--petclinic.woge.enabled=$wogeEnabled",
        ) as ConfigurableWebServerApplicationContext

    private fun visitCount(app: ConfigurableWebServerApplicationContext, description: String): Int =
        app.getBean(OwnerRepository::class.java).findById(1).orElseThrow().getPet(1).visits.count {
            it.description == description
        }

    private fun assertLoginRedirect(response: HttpResponse<String>) {
        assertEquals(302, response.statusCode())
        assertEquals("/login", URI.create(response.headers().firstValue("location").orElseThrow()).path)
    }
}

private class ClinicClient(app: ConfigurableWebServerApplicationContext) {
    private val origin = "http://127.0.0.1:${requireNotNull(app.webServer).port}"
    private val client = HttpClient.newBuilder().cookieHandler(CookieManager()).build()

    fun get(path: String, accept: String = "text/html"): HttpResponse<String> =
        client.send(
            HttpRequest.newBuilder(URI.create(origin + path))
                .header("Accept", accept)
                .header("Accept-Language", "en")
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    fun postForm(path: String, fields: Map<String, String>): HttpResponse<String> {
        val body = fields.entries.joinToString("&") { (name, value) -> "$name=" + URLEncoder.encode(value, UTF_8) }
        return client.send(
            HttpRequest.newBuilder(URI.create(origin + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept-Language", "en")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
    }

    fun login() {
        val login = Jsoup.parse(get("/login").body())
        val token = requireNotNull(login.selectFirst("input[name=_csrf]")).attr("value")
        val loggedIn = postForm("/login", mapOf(
            "_csrf" to token,
            "username" to "reference",
            "password" to "local-test-only",
        ))
        assertEquals(302, loggedIn.statusCode())
    }
}

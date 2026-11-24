package org.springframework.samples.petclinic.woge

import dev.woge.host.ACTION_NAVIGATION_HEADER
import dev.woge.host.ACTION_VALIDATION_HEADER
import dev.woge.protocol.PatchStreamEvent
import dev.woge.protocol.PatchStreamV1
import dev.woge.protocol.ReplacePatch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.boot.SpringApplication
import org.springframework.boot.web.server.context.ConfigurableWebServerApplicationContext
import org.springframework.samples.petclinic.PetClinicApplication
import org.springframework.samples.petclinic.owner.Owner
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
import java.util.UUID

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
            assertEquals(404, client.get("/woge/owner-suggestions?lastName=Fr", "application/json").statusCode())
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

    @Test
    fun `enhanced visit uses the secured upstream validation and saves exactly once`(@TempDir database: Path) {
        val databaseUrl = "jdbc:h2:file:${database.resolve("petclinic").toAbsolutePath()}"
        val action = "/owners/1/pets/1/visits/new"
        val visit = "Check <and> review"
        val due = LocalDate.now().plusDays(2).toString()
        startClinic(databaseUrl, wogeEnabled = true, initMode = "always").use { app ->
            val client = ClinicClient(app)
            client.login()
            val page = Jsoup.parse(client.get("/woge$action").body())
            assertEquals(200, client.get("/assets/woge/index.js", "*/*").statusCode())
            val form = requireNotNull(page.selectFirst("form[data-woge-action]"))
            assertEquals(action, form.attr("action"))
            val csrf = requireNotNull(form.selectFirst("input[name=_csrf]")).`val`()
            val epoch = requireNotNull(page.selectFirst("meta[name=woge-page-epoch]")).attr("content")
            val region = requireNotNull(form.closest("[data-woge-region]")).attr("data-woge-region")
            val headers = mapOf(
                "Accept" to PatchStreamV1.MEDIA_TYPE,
                "Woge-Page-Epoch" to epoch,
                "Woge-Interaction" to "1",
                "Woge-Region-Revisions" to "$region:0",
                "Origin" to client.origin,
            )

            fun submit(
                date: String,
                token: String? = csrf,
                overrides: Map<String, String> = emptyMap(),
                path: String = action,
                extraFields: Map<String, String> = emptyMap(),
                knownLength: Boolean = true,
            ): HttpResponse<ByteArray> {
                val fields = mapOf("date" to date, "description" to visit) + extraFields
                return client.postEnhanced(
                    path,
                    if (token == null) fields else fields + ("_csrf" to token),
                    headers + ("Woge-Request-Identity" to UUID.randomUUID().toString()) + overrides,
                    knownLength,
                )
            }

            assertEquals(403, submit(due, token = null).statusCode())
            assertEquals(403, submit(due, overrides = mapOf("Origin" to "https://other.example")).statusCode())
            assertEquals(400, submit(due, overrides = mapOf("Woge-Request-Identity" to "malformed")).statusCode())
            assertEquals(400, submit(due, overrides = mapOf("Woge-Region-Revisions" to "unknown:0")).statusCode())
            assertEquals(404, submit(due, path = "/owners/1/pets/2/visits/new").statusCode())
            val unsupported = submit(due, overrides = mapOf("Accept" to "application/vnd.woge.patch-stream; version=2"))
            assertEquals(406, unsupported.statusCode())
            assertEquals("unsupported-version", unsupported.headers().firstValue("Woge-Protocol-Error").orElseThrow())

            assertEquals(413, submit(due, extraFields = mapOf("extra" to "x".repeat(4096))).statusCode())
            assertEquals(
                413,
                submit(
                    due,
                    extraFields = mapOf("extra" to "é".repeat(1500), "ü".repeat(700) to "x"),
                    knownLength = false,
                ).statusCode(),
            )

            val invalid = submit(LocalDate.now().minusDays(1).toString(), knownLength = false)
            assertEquals(400, invalid.statusCode())
            assertEquals("visit-errors", invalid.headers().firstValue(ACTION_VALIDATION_HEADER).orElseThrow())
            assertTrue(invalid.headers().firstValue("Cache-Control").orElseThrow().contains("no-store"))
            val decoder = PatchStreamV1.decoder()
            val events = decoder.feed(invalid.body())
            decoder.finish()
            val patch = events.filterIsInstance<PatchStreamEvent.PatchFrame>().single().patch as ReplacePatch
            assertEquals(PatchStreamEvent.Complete(1), events.last())
            assertEquals(epoch, patch.target.pageEpoch.value)
            assertEquals(region, patch.target.region.value)
            assertEquals(1L, patch.revision.next.value)
            val validation = Jsoup.parseBodyFragment(patch.html.value)
            assertEquals(
                "Visit date must be in the future",
                requireNotNull(validation.selectFirst("#visit-errors [href=#visit-date]")).text(),
            )
            assertEquals(visit, requireNotNull(validation.selectFirst("#visit-description")).`val`())
            for (id in listOf("visit-date", "visit-description")) {
                assertEquals(id, requireNotNull(form.selectFirst("#$id")).attr("data-woge-state-key"))
                assertEquals(id, requireNotNull(validation.selectFirst("#$id")).attr("data-woge-state-key"))
            }
            assertEquals(0, visitCount(app, visit))

            val correctedToken = requireNotNull(validation.selectFirst("input[name=_csrf]")).`val`()
            val saved = submit(
                due,
                token = correctedToken,
                overrides = mapOf("Woge-Interaction" to "2", "Woge-Region-Revisions" to "$region:1"),
            )
            assertEquals(200, saved.statusCode())
            assertTrue(saved.body().isEmpty())
            assertEquals("/owners/1", saved.headers().firstValue(ACTION_NAVIGATION_HEADER).orElseThrow())
            val owner = Jsoup.parse(client.get("/owners/1").body())
            assertEquals("Your visit has been booked", requireNotNull(owner.selectFirst("#success-message")).text())
            assertTrue(owner.text().contains(visit))
            assertTrue(client.get("/woge/owners/1").body().contains("Check &lt;and&gt; review"))
            assertEquals(1, visitCount(app, visit))
        }
    }

    @Test
    fun `secured suggestions project eight owners and native paging reaches the rest`(@TempDir database: Path) {
        val databaseUrl = "jdbc:h2:file:${database.resolve("petclinic").toAbsolutePath()}"
        val path = "/woge/owner-suggestions"
        startClinic(databaseUrl, wogeEnabled = true, initMode = "always").use { app ->
            val owners = app.getBean(OwnerRepository::class.java)
            owners.saveAll((1..80).map { number ->
                Owner().apply {
                    firstName = "Resident " + number.toString().padStart(2, '0')
                    lastName = "North"
                    address = "Fixture street"
                    city = "Fixture town"
                    telephone = "1234567890"
                }
            })
            val client = ClinicClient(app)
            val anonymous = client.get("$path?lastName=No", accept = "application/json")
            assertTrue(anonymous.statusCode() in setOf(302, 401))
            assertTrue(!anonymous.body().contains("Resident"))
            client.login()

            val suggestion = client.get("$path?lastName=No", accept = "application/json")
            assertEquals(200, suggestion.statusCode())
            assertTrue(suggestion.headers().firstValue("Cache-Control").orElseThrow().contains("no-store"))
            val matches = Json.parseToJsonElement(suggestion.body()).jsonArray
            assertEquals(8, matches.size)
            assertEquals(setOf("id", "label"), matches.first().jsonObject.keys)
            assertEquals("Resident 01 North", matches.first().jsonObject["label"]?.jsonPrimitive?.content)
            for (prefix in listOf("N", "Nor%25", "No_")) {
                val result = client.get("$path?lastName=$prefix", accept = "application/json")
                assertEquals(200, result.statusCode())
                assertEquals(0, Json.parseToJsonElement(result.body()).jsonArray.size)
            }
            assertEquals(400, client.get("$path?lastName=${"x".repeat(41)}", "application/json").statusCode())
            assertEquals(400, client.get("/woge/owners?page=-1").statusCode())

            var pagePath = "/woge/owners?lastName=No"
            repeat(4) { pageIndex ->
                val response = client.get(pagePath)
                assertEquals(200, response.statusCode())
                assertTrue(
                    response.headers().firstValue("Content-Security-Policy").orElseThrow().contains("script-src 'self'"),
                )
                val page = Jsoup.parse(response.body())
                val results = page.select("ul[aria-label=Owner search results] a")
                assertEquals(20, results.size)
                if (pageIndex < 3) {
                    pagePath = requireNotNull(page.selectFirst("a:containsOwn(Next page)")).attr("href")
                } else {
                    assertEquals("Resident 80 North", results.last()!!.text())
                    assertTrue(page.select("a:containsOwn(Next page)").isEmpty())
                    assertEquals(200, client.get(results.last()!!.attr("href")).statusCode())
                }
            }
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
    val origin = "http://127.0.0.1:${requireNotNull(app.webServer).port}"
    private val client = HttpClient.newBuilder().cookieHandler(CookieManager()).build()

    fun get(path: String, accept: String = "text/html"): HttpResponse<String> =
        client.send(
            HttpRequest.newBuilder(URI.create(origin + path))
                .header("Accept", accept)
                .header("Accept-Language", "en")
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    fun postForm(path: String, fields: Map<String, String>): HttpResponse<String> =
        post(path, fields, emptyMap(), HttpResponse.BodyHandlers.ofString())

    fun postEnhanced(
        path: String,
        fields: Map<String, String>,
        headers: Map<String, String>,
        knownLength: Boolean,
    ): HttpResponse<ByteArray> =
        post(path, fields, headers, HttpResponse.BodyHandlers.ofByteArray(), knownLength)

    private fun <Body> post(
        path: String,
        fields: Map<String, String>,
        headers: Map<String, String>,
        responseBody: HttpResponse.BodyHandler<Body>,
        knownLength: Boolean = true,
    ): HttpResponse<Body> {
        val body = fields.entries.joinToString("&") { (name, value) ->
            URLEncoder.encode(name, UTF_8) + "=" + URLEncoder.encode(value, UTF_8)
        }
        val publisher = if (knownLength) HttpRequest.BodyPublishers.ofString(body) else
            HttpRequest.BodyPublishers.ofInputStream { body.byteInputStream(UTF_8) }
        return client.send(
            HttpRequest.newBuilder(URI.create(origin + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept-Language", "en")
                .apply { headers.forEach { (name, value) -> header(name, value) } }
                .POST(publisher)
                .build(),
            responseBody,
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

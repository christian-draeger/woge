package org.springframework.samples.petclinic.woge

import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringApplication
import org.springframework.boot.web.server.context.ConfigurableWebServerApplicationContext
import org.springframework.samples.petclinic.PetClinicApplication
import java.net.CookieManager
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets.UTF_8
import java.time.LocalDate
import java.util.UUID

class ClinicAdoptionTest {
    @Test
    fun `Woge reads and submits visits through the existing secured application`() {
        val app = SpringApplication.run(
            PetClinicApplication::class.java,
            "--server.address=127.0.0.1",
            "--server.port=0",
            "--spring.security.user.name=reference",
            "--spring.security.user.password=local-test-only",
        ) as ConfigurableWebServerApplicationContext
        app.use {
            val origin = "http://127.0.0.1:${requireNotNull(app.webServer).port}"
            val client = HttpClient.newBuilder().cookieHandler(CookieManager()).build()

            fun get(path: String): HttpResponse<String> = client.send(
                HttpRequest.newBuilder(URI.create(origin + path)).build(),
                HttpResponse.BodyHandlers.ofString(),
            )

            fun postForm(path: String, fields: Map<String, String>): HttpResponse<String> {
                val body = fields.entries.joinToString("&") { (name, value) ->
                    "$name=" + URLEncoder.encode(value, UTF_8)
                }
                return client.send(
                    HttpRequest.newBuilder(URI.create(origin + path))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
            }

            val anonymous = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("$origin/woge/owners")).header("Accept", "text/html").build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            assertEquals(302, anonymous.statusCode())
            assertTrue(anonymous.headers().firstValue("location").orElseThrow().endsWith("/login"))

            val login = Jsoup.parse(get("/login").body())
            val loginToken = requireNotNull(login.selectFirst("input[name=_csrf]")).attr("value")
            val loggedIn = postForm("/login", mapOf(
                "_csrf" to loginToken,
                "username" to "reference",
                "password" to "local-test-only",
            ))
            assertEquals(302, loggedIn.statusCode())

            val search = get("/woge/owners?lastName=Franklin")
            assertEquals(200, search.statusCode())
            assertTrue(Jsoup.parse(search.body()).selectFirst("a[href=/woge/owners/1]")!!.text().contains("Franklin"))
            assertTrue(get("/owners/1").body().contains("Franklin"))
            assertTrue(get("/api/reference/owners/1").body().contains("Franklin"))

            val owner = get("/woge/owners/1")
            val formPath = Jsoup.parse(owner.body()).selectFirst("a[href*=/visits/new]")!!.attr("href")
            val formPage = get(formPath)
            assertEquals(200, formPage.statusCode())
            val form = Jsoup.parse(formPage.body()).selectFirst("form")!!
            val action = form.attr("action")
            val token = form.selectFirst("input[name=_csrf]")!!.attr("value")

            fun submit(description: String, csrf: String?): HttpResponse<String> {
                val fields = buildMap {
                    put("date", LocalDate.now().plusDays(2).toString())
                    put("description", description)
                    if (csrf != null) put("_csrf", csrf)
                }
                return postForm(action, fields)
            }

            val visit = "Reference visit ${UUID.randomUUID()}"
            assertEquals(403, submit(visit, null).statusCode())
            assertTrue(!get("/woge/owners/1").body().contains(visit))
            val invalid = submit("", token)
            assertEquals(200, invalid.statusCode())
            assertTrue(Jsoup.parse(invalid.body()).select("form input[name=description]").isNotEmpty())

            val saved = submit(visit, token)
            assertEquals(302, saved.statusCode())
            assertEquals("/owners/1", URI.create(saved.headers().firstValue("location").orElseThrow()).path)
            assertTrue(get("/owners/1").body().contains(visit))
            assertTrue(get("/woge/owners/1").body().contains(visit))
        }
    }
}

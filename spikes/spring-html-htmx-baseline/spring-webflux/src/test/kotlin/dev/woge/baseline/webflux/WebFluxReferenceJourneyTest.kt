package dev.woge.baseline.webflux

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.BodyInserters

@SpringBootTest(
    properties = [
        "baseline.delay.summary=0",
        "baseline.delay.tasks=0",
        "baseline.delay.activity=0",
    ],
)
@AutoConfigureWebTestClient
class WebFluxReferenceJourneyTest {
    @Autowired
    lateinit var client: WebTestClient

    @Test
    fun `shell defers regions and full page remains available`() {
        client.get().uri("/projects/woge")
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .value { html ->
                assertThat(html).contains("hx-get=\"/projects/woge/regions/summary\"")
                assertThat(html).contains("aria-busy=\"true\"")
                assertThat(html).contains("?full=true")
            }

        client.get().uri("/projects/woge?full=true")
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .value { html ->
                assertThat(html).contains("Define the walking skeleton")
                assertThat(html).contains("aria-busy=\"false\"")
            }

        listOf("summary", "tasks", "activity").forEach { region ->
            client.get().uri("/projects/woge/regions/$region")
                .exchange()
                .expectStatus().isOk
                .expectBody(String::class.java)
                .value { html -> assertThat(html).contains("aria-busy=\"false\"") }
        }
    }

    @Test
    fun `native and enhanced actions preserve their respective web contracts`() {
        client.post().uri("/projects/woge/tasks")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(BodyInserters.fromFormData("title", ""))
            .exchange()
            .expectStatus().isEqualTo(422)
            .expectBody(String::class.java)
            .value { html ->
                assertThat(html).contains("Enter a task title.")
                assertThat(html).contains("<html")
            }

        client.post().uri("/projects/woge/tasks")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .header("HX-Request", "true")
            .body(
                BodyInserters.fromFormData("title", "Test WebFlux patches")
                    .with("owner", "Ada"),
            )
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .value { html ->
                assertThat(html).contains("id=\"task-create\"")
                assertThat(html).contains("hx-swap-oob=\"outerHTML\"")
                assertThat(html).contains("Test WebFlux patches")
            }

        client.post().uri("/projects/woge/tasks/3/status")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .header("HX-Request", "true")
            .body(BodyInserters.fromFormData("completed", "true"))
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .value { html ->
                assertThat(html).contains("Completed")
                assertThat(html).contains("Task completed: Test WebFlux patches")
            }

        client.post().uri("/projects/woge/tasks")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(BodyInserters.fromFormData("title", "Native WebFlux task"))
            .exchange()
            .expectStatus().is3xxRedirection
            .expectHeader().location("/projects/woge?full=true")
    }
}

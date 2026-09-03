package dev.woge.baseline.mvc

import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@SpringBootTest(
    properties = [
        "baseline.delay.summary=0",
        "baseline.delay.tasks=0",
        "baseline.delay.activity=0",
    ],
)
@AutoConfigureMockMvc
class MvcReferenceJourneyTest {
    @Autowired
    lateinit var mvc: MockMvc

    @Test
    fun `shell defers regions and full page remains available`() {
        mvc.get("/projects/woge")
            .andExpect {
                status { isOk() }
                content { string(containsString("hx-get=\"/projects/woge/regions/summary\"")) }
                content { string(containsString("aria-busy=\"true\"")) }
                content { string(containsString("?full=true")) }
            }

        mvc.get("/projects/woge?full=true")
            .andExpect {
                status { isOk() }
                content { string(containsString("Define the walking skeleton")) }
                content { string(containsString("aria-busy=\"false\"")) }
            }

        listOf("summary", "tasks", "activity").forEach { region ->
            mvc.get("/projects/woge/regions/$region")
                .andExpect {
                    status { isOk() }
                    content { string(containsString("aria-busy=\"false\"")) }
                }
        }
    }

    @Test
    fun `native and enhanced actions preserve their respective web contracts`() {
        mvc.post("/projects/woge/tasks") {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            param("title", "")
        }.andExpect {
            status { isUnprocessableContent() }
            content { string(containsString("Enter a task title.")) }
            content { string(containsString("<html")) }
        }

        mvc.post("/projects/woge/tasks") {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            header("HX-Request", "true")
            param("title", "Test MVC patches")
            param("owner", "Ada")
        }.andExpect {
            status { isOk() }
            content { string(containsString("id=\"task-create\"")) }
            content { string(containsString("hx-swap-oob=\"outerHTML\"")) }
            content { string(containsString("Test MVC patches")) }
        }

        mvc.post("/projects/woge/tasks/3/status") {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            header("HX-Request", "true")
            param("completed", "true")
        }.andExpect {
            status { isOk() }
            content { string(containsString("Completed")) }
            content { string(containsString("Task completed: Test MVC patches")) }
        }

        mvc.post("/projects/woge/tasks") {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            param("title", "Native MVC task")
        }.andExpect {
            status { is3xxRedirection() }
            redirectedUrl("/projects/woge?full=true")
        }
    }
}

package dev.woge.html

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(WogeDevelopmentHook::class)
public class TestDevelopmentHeadContribution : DevelopmentHeadContribution {
    override fun writeTo(head: HtmlWriter) {
        head.metadata("woge-test", "development")
    }
}

class DevelopmentHeadContributionTest {
    @AfterEach
    fun clearProperty() {
        System.clearProperty("woge.development")
    }

    @Test
    fun `production rendering ignores development contributions on the classpath`() {
        assertEquals("<head><title>A</title></head>", renderHtml { head { title("A") } })
    }

    @Test
    fun `development rendering appends contributions at the end of head only`() {
        System.setProperty("woge.development", "true")

        assertEquals(
            "<html><head><title>A</title><meta name=\"woge-test\" content=\"development\"></head>" +
                "<body></body></html>",
            renderHtml {
                html {
                    head { title("A") }
                    body {}
                }
            },
        )
    }
}

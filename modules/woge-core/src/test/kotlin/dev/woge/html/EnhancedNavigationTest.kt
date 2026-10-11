package dev.woge.html

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class EnhancedNavigationTest {
    @Test
    fun `navigation markers do not change native href semantics`() {
        assertEquals(
            "<nav data-woge-navigation=\"true\"><a href=\"/notes\" data-woge-navigation=\"false\">Notes</a></nav>" +
                "<main data-woge-navigation-region=\"route\"></main>" +
                "<meta name=\"description\" data-woge-navigation-meta=\"\">",
            renderHtml {
                nav(attributes = { enhancedNavigation() }) {
                    a(attributes = {
                        url("href", applicationUrl("/notes"))
                        enhancedNavigation(false)
                    }) { text("Notes") }
                }
                main(attributes = { navigationRegion("route") })
                meta {
                    attribute("name", "description")
                    navigationMetadata()
                }
            },
        )
    }

    @Test
    fun `invalid route names fail before rendering`() {
        for (name in listOf("", "has spaces", "route\"", "a".repeat(65))) {
            assertThrows(IllegalArgumentException::class.java) {
                renderHtml { main(attributes = { navigationRegion(name) }) }
            }
        }
    }
}

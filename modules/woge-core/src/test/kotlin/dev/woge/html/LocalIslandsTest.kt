package dev.woge.html

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalIslandsTest {
    private val note = IslandType<String>("note", 1) { IslandProps(mapOf("text" to it)) }

    @Test
    fun `fallback and escaped inert props contain a type but no asset URL`() {
        val html =
            renderHtml {
                island(note, "</script><img onerror=evil>\n\"&", key = "note-1") {
                    p { text("Native form remains useful") }
                }
            }
        assertTrue(html.startsWith("<div data-woge-island=\"note\""))
        assertTrue(html.contains("data-woge-state-key=\"note-1\""))
        assertTrue(html.contains("<p>Native form remains useful</p>"))
        assertTrue(html.contains("<template data-woge-island-props=\"\">"))
        assertTrue(html.contains("\\u003c/script\\u003e"))
        assertTrue(html.contains("\\u000a"))
        assertFalse(html.contains("<script"))
        assertFalse(html.contains(".js"))
        assertFalse(html.contains("<img"))
    }

    @Test
    fun `registry generates one allowlist with versioned local module and typed schema`() {
        val html =
            renderHtml {
                islandManifest(IslandRegistry(IslandModule(note, "/assets/note.js", "v1")))
                islandLoader()
            }
        assertTrue(html.contains("\"url\":\"/assets/note.js?v=v1\""))
        assertTrue(html.contains("\"mount\":\"mount\",\"propsVersion\":1"))
        assertTrue(html.contains("id=\"woge-islands\""))
        assertTrue(html.contains("src=\"/assets/woge/islands.js\""))
    }

    @Test
    fun `manifest refuses external absolute executable encoded and ambiguous module URLs`() {
        for (path in listOf(
            "https://example.com/note.js",
            "//example.com/note.js",
            "javascript:evil",
            "note.js",
            "/assets/../note.js",
            "/assets/%2f/note.js",
            "/assets/note.js?evil=1",
            "/assets/note.js#evil",
            "/assets/\\note.js",
            "/assets/note.js\n",
        )) {
            assertThrows(IllegalArgumentException::class.java) { IslandModule(note, path, "v1") }
        }
        assertThrows(IllegalArgumentException::class.java) { IslandModule(note, "/note.js", "bad version") }
        assertThrows(IllegalArgumentException::class.java) { IslandModule(note, "/note.js", "v1", "bad()") }
    }

    @Test
    fun `declaration and registry validate names versions keys and duplicate types`() {
        assertThrows(
            IllegalArgumentException::class.java,
        ) { IslandType<String>("bad name", 1) { IslandProps(mapOf()) } }
        assertThrows(IllegalArgumentException::class.java) { IslandType<String>("note", 0) { IslandProps(mapOf()) } }
        val module = IslandModule(note, "/note.js", "v1")
        assertThrows(IllegalArgumentException::class.java) { IslandRegistry(module, module) }
        assertThrows(IllegalStateException::class.java) {
            renderHtml {
                islandManifest(IslandRegistry(module))
                islandManifest(IslandRegistry(module))
            }
        }
        assertThrows(IllegalArgumentException::class.java) { renderHtml { island(note, "", key = "bad key") {} } }
        assertEquals("{\"\\u2028\":\"\\u0000\\u2029\"}", IslandProps(mapOf("\u2028" to "\u0000\u2029")).json())
    }
}

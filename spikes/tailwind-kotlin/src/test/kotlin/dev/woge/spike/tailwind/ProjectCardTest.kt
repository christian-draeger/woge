package dev.woge.spike.tailwind

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectCardTest {
    @Test
    fun `runtime choices map to complete static candidates`() {
        val info = renderProjectCard("Roadmap", ProjectTone.INFO)
        val warning = renderProjectCard("Roadmap", ProjectTone.WARNING)

        assertTrue("bg-brand-500 text-white" in info)
        assertTrue("bg-amber-100 text-amber-950" in warning)
        assertTrue("\${" !in info)
        assertTrue("\${" !in warning)
    }

    @Test
    fun `Tailwind changes only style assets and class attributes`() {
        val styled = renderProjectCard("Roadmap", ProjectTone.INFO)
            .replace(Regex("<link[^>]+>"), "")
            .replace(Regex(" class=\"[^\"]*\""), "")

        assertEquals(renderPlainProjectCard("Roadmap"), styled)
    }

    @Test
    fun `styling fixture keeps HTML text and attribute contexts escaped`() {
        val rendered = renderProjectCard(
            "<script>alert('x')</script>",
            ProjectTone.INFO,
            listOf("/assets/a.css?theme=\"dark\"&mode=test"),
        )

        assertTrue("<script>" !in rendered)
        assertTrue("&lt;script&gt;alert('x')&lt;/script&gt;" in rendered)
        assertTrue("href=\"/assets/a.css?theme=&quot;dark&quot;&amp;mode=test\"" in rendered)
    }
}

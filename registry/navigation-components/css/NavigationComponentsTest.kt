// SPDX-License-Identifier: Apache-2.0
package dev.woge.registry.template

import dev.woge.html.applicationUrl
import dev.woge.html.renderHtml
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NavigationComponentsTest {
    private val links =
        listOf(
            NavigationLink("الرئيسية <&>", applicationUrl("/home?q=a&b=c")),
            NavigationLink("Current", applicationUrl("/page/2"), current = true),
        )

    @Test
    fun `navigation stays native with one current link and no tab or menu role`() {
        val fragments =
            listOf(
                renderHtml { breadcrumb("Path", links) },
                renderHtml { pagination("Pages", links) },
                renderHtml { navigationTabs("Views", links) },
                renderHtml { navigationMenu("Main", links) },
                renderHtml { navigationDropdown("More", links) },
            )
        fragments.forEach { html ->
            assertTrue(html.contains("<nav aria-label="))
            assertTrue(html.contains("href=\"/home?q=a&amp;b=c\""))
            assertTrue(html.contains("aria-current=\"page\""))
            assertTrue(html.contains("الرئيسية &lt;&amp;&gt;"))
            assertTrue(!html.contains("role="))
            assertTrue(!html.contains("<script"))
        }
        assertTrue(fragments.first().contains("<ol>"))
        assertTrue(fragments.last().contains("<summary>More</summary>"))
        assertThrows(IllegalArgumentException::class.java) {
            renderHtml { pagination("Pages", links + links.last()) }
        }
    }
}

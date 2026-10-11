// SPDX-License-Identifier: Apache-2.0
package dev.woge.registry.template

import dev.woge.html.Attributes
import dev.woge.html.HtmlUrl
import dev.woge.html.HtmlWriter
import dev.woge.html.a
import dev.woge.ui.disclosure

public data class NavigationLink(
    public val label: String,
    public val href: HtmlUrl,
    public val current: Boolean = false,
)

public fun HtmlWriter.breadcrumb(
    label: String,
    links: List<NavigationLink>,
) {
    navigationList(label, links, "breadcrumb", ordered = true)
}

public fun HtmlWriter.pagination(
    label: String,
    pages: List<NavigationLink>,
) {
    navigationList(label, pages, "pagination")
}

/** URL tabs are ordinary page links, not a JavaScript tablist with hidden panels. */
public fun HtmlWriter.navigationTabs(
    label: String,
    tabs: List<NavigationLink>,
) {
    navigationList(label, tabs, "tabs")
}

public fun HtmlWriter.navigationMenu(
    label: String,
    links: List<NavigationLink>,
) {
    navigationList(label, links, "menu")
}

/** A navigation dropdown uses native disclosure, not the application-menu ARIA keyboard contract. */
public fun HtmlWriter.navigationDropdown(
    label: String,
    links: List<NavigationLink>,
) {
    disclosure(summary = { text(label) }, attributes = { navigationPart("dropdown") }) {
        navigationList(label, links, "menu")
    }
}

private fun HtmlWriter.navigationList(
    label: String,
    links: List<NavigationLink>,
    part: String,
    ordered: Boolean = false,
) {
    require(label.isNotBlank()) { "Navigation needs an accessible name" }
    require(links.count { it.current } <= 1) { "Only one destination can be current" }
    element("nav", attributes = {
        aria("label", label)
        navigationPart(part)
    }) {
        element(if (ordered) "ol" else "ul") {
            links.forEach { link ->
                element("li") {
                    a(attributes = {
                        url("href", link.href)
                        if (link.current) aria("current", "page")
                        navigationPart("link")
                    }) { text(link.label) }
                }
            }
        }
    }
}

internal fun Attributes.navigationPart(part: String) {
    data("woge-scope", "woge-navigation-components")
    data("part", part)
    classes("woge-navigation-$part")
}

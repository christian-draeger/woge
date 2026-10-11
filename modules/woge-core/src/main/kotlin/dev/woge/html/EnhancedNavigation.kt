package dev.woge.html

/** Marks this anchor or container eligible; false explicitly opts out of inherited eligibility. */
public fun Attributes.enhancedNavigation(enabled: Boolean = true) {
    data("woge-navigation", if (enabled) "true" else "false")
}

/** Declares the single document route region. Use the same name across compatible pages. */
public fun Attributes.navigationRegion(name: String) {
    require(name.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Invalid navigation region name" }
    data("woge-navigation-region", name)
}

/** Marks a head meta element for document navigation updates; its name must remain stable. */
public fun Attributes.navigationMetadata() {
    data("woge-navigation-meta", "")
}

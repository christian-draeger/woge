package fixture

import dev.woge.html.Attributes

// WOGE-XSS-002: ordinary strings must not enter validated URL-bearing attributes.
public fun renderInvalidUrl(attributes: Attributes) {
    attributes.url("href", "javascript:alert('x')")
}

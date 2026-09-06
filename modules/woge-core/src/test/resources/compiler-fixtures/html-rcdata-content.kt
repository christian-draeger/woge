package fixture

import dev.woge.html.renderHtml
import dev.woge.html.strong
import dev.woge.html.title

internal val brokenMarkup: String = renderHtml { title("page") { strong {} } }

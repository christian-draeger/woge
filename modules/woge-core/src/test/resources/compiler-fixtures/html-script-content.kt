package fixture

import dev.woge.html.renderHtml
import dev.woge.html.script

internal val brokenMarkup: String = renderHtml { script { text("alert('unsafe')") } }

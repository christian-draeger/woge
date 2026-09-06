package fixture

import dev.woge.html.br
import dev.woge.html.renderHtml

internal val brokenMarkup: String = renderHtml { br { text("void elements have no body") } }

package fixture

import dev.woge.html.renderHtml
import dev.woge.html.section

internal val typedLinks: String = renderHtml { section { text("safe") } }

package fixture

import dev.woge.css.declarations
import dev.woge.html.renderHtml
import dev.woge.html.style

internal val invalid: String = renderHtml {
    style(declarations("color: red;"))
}

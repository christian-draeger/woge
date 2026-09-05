package fixture

import dev.woge.css.stylesheet
import dev.woge.html.renderHtml

internal val invalid: String = renderHtml {
    element("div", attributes = { styles(stylesheet(".card { color: red; }")) })
}

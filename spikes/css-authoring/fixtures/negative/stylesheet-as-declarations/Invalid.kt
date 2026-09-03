package fixture

import woge.css.styleAttribute
import woge.css.stylesheet

internal val invalid: String = styleAttribute(stylesheet(".card { color: red; }"))

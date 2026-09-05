package dev.woge.spring.mvc

import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.servlet.HandlerMapping

/** Reads a path variable extracted by Spring MVC's URL handler mapping. */
public fun HttpServletRequest.pathVariable(name: String): String {
    val variables =
        getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as? Map<*, *>
            ?: error("Spring MVC did not expose URI template variables for '$requestURI'")
    return variables[name] as? String
        ?: error("Spring MVC route '$requestURI' has no '$name' path variable")
}

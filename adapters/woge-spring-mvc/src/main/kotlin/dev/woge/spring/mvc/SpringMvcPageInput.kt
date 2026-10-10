package dev.woge.spring.mvc

import dev.woge.host.PageRoute
import dev.woge.host.RouteParameters
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.servlet.HandlerMapping

/** Decodes route-specific page input on the Servlet request thread. */
public fun interface SpringMvcPageInput<Input : Any> {
    public fun decode(request: HttpServletRequest): Input
}

/** Reads the typed input of a generated route from the Spring MVC path variables and query string. */
public fun <Input : Any> PageRoute<Input>.springMvcInput(): SpringMvcPageInput<Input> =
    SpringMvcPageInput { request ->
        val variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as? Map<*, *>
        decode(
            object : RouteParameters {
                override fun path(name: String): String? = variables?.get(name) as? String

                override fun query(name: String): String? = request.getParameter(name)
            },
        )
    }

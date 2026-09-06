package dev.woge.spring.mvc

import jakarta.servlet.http.HttpServletRequest

/** Decodes route-specific page input on the Servlet request thread. */
public fun interface SpringMvcPageInput<Input : Any> {
    public fun decode(request: HttpServletRequest): Input
}

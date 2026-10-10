package dev.woge.spring.mvc

import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.multipart.MultipartHttpServletRequest
import org.springframework.web.multipart.MultipartResolver
import org.springframework.web.multipart.support.StandardServletMultipartResolver

/**
 * Register as Spring's `multipartResolver` bean. Woge routes read the raw body after security checks;
 * other Spring controllers retain [delegate] and its normal Servlet multipart behavior.
 */
public class WogeMultipartResolver(
    actionPaths: Set<String>,
    private val delegate: MultipartResolver = StandardServletMultipartResolver(),
) : MultipartResolver {
    private val actionPaths = actionPaths.toSet()

    init {
        require(actionPaths.isNotEmpty() && actionPaths.all { it.startsWith("/") && '?' !in it && '#' !in it }) {
            "Woge upload action paths must be non-empty absolute application paths"
        }
    }

    override fun isMultipart(request: HttpServletRequest): Boolean =
        request.requestURI.removePrefix(request.contextPath) !in actionPaths && delegate.isMultipart(request)

    override fun resolveMultipart(request: HttpServletRequest): MultipartHttpServletRequest =
        delegate.resolveMultipart(request)

    override fun cleanupMultipart(request: MultipartHttpServletRequest): Unit = delegate.cleanupMultipart(request)
}

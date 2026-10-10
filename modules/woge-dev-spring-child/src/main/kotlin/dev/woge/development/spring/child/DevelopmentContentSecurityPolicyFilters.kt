package dev.woge.development.spring.child

import dev.woge.development.ExperimentalWogeDevelopmentApi
import dev.woge.development.client.DevelopmentClientSettings
import dev.woge.development.client.DevelopmentContentSecurityPolicy
import dev.woge.development.client.FileDevelopmentHeadContribution
import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponseWrapper
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.Ordered
import org.springframework.http.HttpHeaders
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.http.server.reactive.ServerHttpResponseDecorator
import org.springframework.util.ClassUtils
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.util.function.Supplier

private val policyHeaders = listOf("Content-Security-Policy", "Content-Security-Policy-Report-Only")

/** Set by `wogeDev` when the optional Vite adapter runs its dev server next to the application. */
private const val VITE_ORIGIN_VARIABLE = "WOGE_VITE_ORIGIN"

/** Finds the session origin (and the Vite dev server, if any) and rewrites one CSP header value for it. */
@OptIn(ExperimentalWogeDevelopmentApi::class)
internal class DevelopmentPolicyRewriter(
    private val viteOrigin: String? = System.getenv(VITE_ORIGIN_VARIABLE)?.takeIf(String::isNotEmpty),
    private val settings: () -> DevelopmentClientSettings? = FileDevelopmentHeadContribution()::current,
) {
    fun rewrite(
        name: String,
        value: String,
    ): String {
        val current = settings().takeIf { policyHeaders.any { it.equals(name, ignoreCase = true) } } ?: return value
        val withClient =
            DevelopmentContentSecurityPolicy.allow(
                value,
                DevelopmentContentSecurityPolicy.originOf(current),
            )
        return viteOrigin?.let { DevelopmentContentSecurityPolicy.allowVite(withClient, it) } ?: withClient
    }

    fun rewrite(headers: HttpHeaders) {
        policyHeaders.forEach { name ->
            headers.get(name)?.let { values -> headers.put(name, values.map { rewrite(name, it) }) }
        }
    }
}

/**
 * Registers the CSP filters while `wogeDev` runs, so a strict application policy still accepts the
 * development client. Without `WOGE_DEV_CLIENT_FILE` nothing is registered.
 */
@OptIn(ExperimentalWogeDevelopmentApi::class)
public class DevelopmentContentSecurityPolicyInitializer :
    ApplicationContextInitializer<ConfigurableApplicationContext> {
    override fun initialize(context: ConfigurableApplicationContext) {
        if (System.getenv(FileDevelopmentHeadContribution.CLIENT_FILE_VARIABLE) == null) return
        val registry = context as? GenericApplicationContext ?: return
        val loader = context.classLoader
        if (ClassUtils.isPresent("jakarta.servlet.Filter", loader)) {
            registry.registerBean(
                "wogeDevelopmentCspServletFilter",
                DevelopmentContentSecurityPolicyServletFilter::class.java,
                Supplier { DevelopmentContentSecurityPolicyServletFilter() },
            )
        }
        if (ClassUtils.isPresent("org.springframework.web.reactive.DispatcherHandler", loader)) {
            registry.registerBean(
                "wogeDevelopmentCspWebFilter",
                DevelopmentContentSecurityPolicyWebFilter::class.java,
                Supplier { DevelopmentContentSecurityPolicyWebFilter() },
            )
        }
    }
}

/** Spring MVC: rewrites CSP headers whenever the application or a security filter sets them. */
public class DevelopmentContentSecurityPolicyServletFilter internal constructor(
    private val rewriter: DevelopmentPolicyRewriter,
) : Filter,
    Ordered {
    public constructor() : this(DevelopmentPolicyRewriter())

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE

    override fun doFilter(
        request: ServletRequest,
        response: ServletResponse,
        chain: FilterChain,
    ) {
        if (response !is HttpServletResponse) return chain.doFilter(request, response)
        chain.doFilter(
            request,
            object : HttpServletResponseWrapper(response) {
                override fun setHeader(
                    name: String,
                    value: String?,
                ) = super.setHeader(name, value?.let { rewriter.rewrite(name, it) })

                override fun addHeader(
                    name: String,
                    value: String?,
                ) = super.addHeader(name, value?.let { rewriter.rewrite(name, it) })
            },
        )
    }
}

/** Spring WebFlux: rewrites CSP headers after every other before-commit action has run. */
public class DevelopmentContentSecurityPolicyWebFilter internal constructor(
    private val rewriter: DevelopmentPolicyRewriter,
) : WebFilter,
    Ordered {
    public constructor() : this(DevelopmentPolicyRewriter())

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE

    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain,
    ): Mono<Void> {
        val response = RewritingResponse(exchange.response, rewriter)
        response.beforeCommit { Mono.empty() }
        return chain.filter(exchange.mutate().response(response).build())
    }

    private class RewritingResponse(
        delegate: ServerHttpResponse,
        private val rewriter: DevelopmentPolicyRewriter,
    ) : ServerHttpResponseDecorator(delegate) {
        override fun beforeCommit(action: Supplier<out Mono<Void>>) {
            super.beforeCommit { action.get().then(Mono.fromRunnable<Void> { rewriter.rewrite(headers) }) }
        }
    }
}

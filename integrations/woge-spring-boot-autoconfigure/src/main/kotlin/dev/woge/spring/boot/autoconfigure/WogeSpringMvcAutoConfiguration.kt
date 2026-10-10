package dev.woge.spring.boot.autoconfigure

import dev.woge.host.FailurePages
import dev.woge.host.WogeObserver
import dev.woge.spring.mvc.DefaultSpringMvcRequestContextFactory
import dev.woge.spring.mvc.SpringMvcRequestContextFactory
import dev.woge.spring.mvc.WogeSpringMvcHandlers
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnResource
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.CacheControl
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.time.Duration
import kotlin.time.toKotlinDuration

/** Spring MVC-specific bean definitions isolated from the optional adapter classpath. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = ["dev.woge.spring.mvc.WogeSpringMvcHandlers"])
internal class WogeSpringMvcAutoConfiguration {
    @Bean
    @ConditionalOnResource(resources = ["classpath:META-INF/woge/assets.properties"])
    public fun wogeSpringMvcAssets(): WebMvcConfigurer =
        object : WebMvcConfigurer {
            override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
                registry
                    .addResourceHandler("/_woge/assets/**")
                    .addResourceLocations("classpath:/META-INF/woge/assets/")
                    .setCacheControl(
                        CacheControl.maxAge(Duration.ofDays(IMMUTABLE_ASSET_CACHE_DAYS)).cachePublic().immutable(),
                    )
            }
        }

    /** Safe-method anonymous default; security-aware applications should provide their own bean. */
    @Bean
    @ConditionalOnMissingBean
    public fun wogeSpringMvcRequestContextFactory(): SpringMvcRequestContextFactory =
        DefaultSpringMvcRequestContextFactory

    /** Shared policy factory used from ordinary Spring MVC URL handler mappings. */
    @Bean
    @ConditionalOnMissingBean
    public fun wogeSpringMvcHandlers(
        properties: WogeProperties,
        contexts: SpringMvcRequestContextFactory,
        runtimeInfo: WogeRuntimeInfo,
        observer: WogeObserver,
        failurePages: FailurePages,
    ): WogeSpringMvcHandlers {
        check(runtimeInfo.adapter == WogeSpringAdapter.MVC) {
            "Servlet Woge configuration requires 'woge.adapter=mvc'."
        }
        return WogeSpringMvcHandlers(
            contexts = contexts,
            asyncTimeout = properties.mvc.asyncTimeout.toKotlinDuration(),
            maxConcurrency = properties.deferred.maxConcurrency,
            regionTimeout = properties.deferred.regionTimeout.toKotlinDuration(),
            observer = observer,
            failurePages = failurePages,
        )
    }
}

package dev.woge.spring.boot.autoconfigure

import dev.woge.spring.webflux.DefaultWebFluxRequestContextFactory
import dev.woge.spring.webflux.WebFluxRequestContextFactory
import dev.woge.spring.webflux.WogeWebFluxHandlers
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import kotlin.time.toKotlinDuration

/** WebFlux-specific bean definitions isolated from the optional adapter classpath. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass(name = ["dev.woge.spring.webflux.WogeWebFluxHandlers"])
internal class WogeWebFluxAutoConfiguration {
    /** Safe-method anonymous default; security-aware applications should provide their own bean. */
    @Bean
    @ConditionalOnMissingBean
    public fun wogeWebFluxRequestContextFactory(): WebFluxRequestContextFactory = DefaultWebFluxRequestContextFactory

    /** Shared policy factory used from familiar functional WebFlux routes. */
    @Bean
    @ConditionalOnMissingBean
    public fun wogeWebFluxHandlers(
        properties: WogeProperties,
        contexts: WebFluxRequestContextFactory,
        runtimeInfo: WogeRuntimeInfo,
    ): WogeWebFluxHandlers {
        check(runtimeInfo.adapter == WogeSpringAdapter.WEBFLUX) {
            "Reactive Woge configuration requires 'woge.adapter=webflux'."
        }
        return WogeWebFluxHandlers(
            contexts = contexts,
            maxConcurrency = properties.deferred.maxConcurrency,
            regionTimeout = properties.deferred.regionTimeout.toKotlinDuration(),
        )
    }
}

package dev.woge.spring.boot.autoconfigure

import dev.woge.host.DeferredRegionsUseCase
import dev.woge.host.FailurePages
import dev.woge.host.PageUseCase
import dev.woge.host.RenderIdentitySecret
import dev.woge.host.WogeObserver
import dev.woge.html.AssetUrls
import dev.woge.protocol.PatchProtocolVersion
import org.apache.commons.logging.LogFactory
import org.springframework.beans.factory.ListableBeanFactory
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnResource
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.context.reactive.ConfigurableReactiveWebApplicationContext
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.core.io.ResourceLoader
import java.util.Base64

internal const val IMMUTABLE_ASSET_CACHE_DAYS = 365L

/** Shared Spring Boot discovery, deterministic adapter selection and startup diagnostics. */
@AutoConfiguration(after = [WogeObservationAutoConfiguration::class])
@ConditionalOnWebApplication
@EnableConfigurationProperties(WogeProperties::class)
@Import(WogeSpringMvcAutoConfiguration::class, WogeWebFluxAutoConfiguration::class)
public class WogeAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public fun wogeRenderIdentitySecret(properties: WogeProperties): RenderIdentitySecret {
        val configured = properties.renderIdentity.secret
        if (configured == null) {
            logger.warn(
                "woge.render-identity.secret is unset; using a process-local secret. " +
                    "Configure a shared secret before running multiple instances or restarting enhanced pages.",
            )
            return RenderIdentitySecret.random()
        }
        return RenderIdentitySecret.of(Base64.getDecoder().decode(configured))
    }

    /** Build-generated assets are shared by MVC and WebFlux, with plain URLs only in wogeDev. */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnResource(resources = ["classpath:META-INF/woge/assets.properties"])
    public fun wogeAssetUrls(resourceLoader: ResourceLoader): AssetUrls =
        AssetUrls.load(resourceLoader.classLoader ?: WogeAutoConfiguration::class.java.classLoader)

    /** Bodyless by default; applications can provide shared not-found and error HTML. */
    @Bean
    @ConditionalOnMissingBean
    public fun wogeFailurePages(): FailurePages = FailurePages.NONE

    /** Framework-neutral fallback when no observation registry or application observer is available. */
    @Bean
    @ConditionalOnMissingBean
    public fun wogeObserver(): WogeObserver = WogeObserver.NONE

    /** Discovers only Woge host entry points; HTML components remain ordinary Kotlin values/functions. */
    @Bean
    @ConditionalOnMissingBean
    public fun wogeApplicationCatalog(beanFactory: ListableBeanFactory): WogeApplicationCatalog =
        WogeApplicationCatalog(
            pageUseCases = beanFactory.getBeanNamesForType(PageUseCase::class.java, true, false).asIterable(),
            deferredRegionUseCases =
                beanFactory.getBeanNamesForType(DeferredRegionsUseCase::class.java, true, false).asIterable(),
        )

    /** Selects one adapter, exposes versions and emits one concise startup diagnostic. */
    @Bean
    @ConditionalOnMissingBean
    public fun wogeRuntimeInfo(
        properties: WogeProperties,
        catalog: WogeApplicationCatalog,
        resourceLoader: ResourceLoader,
        applicationContext: ApplicationContext,
    ): WogeRuntimeInfo {
        requireWogeConfiguration(
            properties.deferred.maxConcurrency > 0 &&
                !properties.deferred.regionTimeout.isNegative &&
                !properties.deferred.regionTimeout.isZero &&
                !properties.mvc.asyncTimeout.isNegative &&
                !properties.mvc.asyncTimeout.isZero,
        ) {
            "Woge requires positive 'woge.deferred.max-concurrency', 'woge.deferred.region-timeout' " +
                "and 'woge.mvc.async-timeout' values."
        }
        val classLoader = resourceLoader.classLoader ?: WogeAutoConfiguration::class.java.classLoader
        val selectedAdapter = WogeAdapterSelector.select(properties.adapter, classLoader)
        val contextAdapter =
            if (applicationContext is ConfigurableReactiveWebApplicationContext) {
                WogeSpringAdapter.WEBFLUX
            } else {
                WogeSpringAdapter.MVC
            }
        requireWogeConfiguration(selectedAdapter == contextAdapter) {
            "Woge selected '$selectedAdapter', but Spring Boot created a '$contextAdapter' web application. " +
                "Set 'woge.adapter=${contextAdapter.name.lowercase()}' or configure " +
                "'spring.main.web-application-type' to match."
        }
        val runtimeInfo =
            WogeRuntimeInfo(
                adapter = selectedAdapter,
                protocolVersion = PatchProtocolVersion.CURRENT.value,
                runtimeVersion = WogeAutoConfiguration::class.java.`package`?.implementationVersion ?: "development",
                pageUseCases = catalog.pageUseCases.size,
                deferredRegionUseCases = catalog.deferredRegionUseCases.size,
            )
        logger.info(
            "Woge initialized: adapter=${runtimeInfo.adapter}, " +
                "protocol=${runtimeInfo.protocolVersion}, runtime=${runtimeInfo.runtimeVersion}, " +
                "pages=${runtimeInfo.pageUseCases}, deferred=${runtimeInfo.deferredRegionUseCases}",
        )
        return runtimeInfo
    }

    private companion object {
        private val logger = LogFactory.getLog(WogeAutoConfiguration::class.java)
    }
}

package dev.woge.spring.boot.autoconfigure

import dev.woge.host.DeferredRegionsUseCase
import dev.woge.host.PageUseCase
import dev.woge.protocol.PatchProtocolVersion
import org.apache.commons.logging.LogFactory
import org.springframework.beans.factory.ListableBeanFactory
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.context.reactive.ConfigurableReactiveWebApplicationContext
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.core.io.ResourceLoader

/** Shared Spring Boot discovery, deterministic adapter selection and startup diagnostics. */
@AutoConfiguration
@ConditionalOnWebApplication
@EnableConfigurationProperties(WogeProperties::class)
@Import(WogeWebFluxAutoConfiguration::class)
public class WogeAutoConfiguration {
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
        val classLoader = resourceLoader.classLoader ?: WogeAutoConfiguration::class.java.classLoader
        val selectedAdapter = WogeAdapterSelector.select(properties.adapter, classLoader)
        val contextAdapter =
            if (applicationContext is ConfigurableReactiveWebApplicationContext) {
                WogeSpringAdapter.WEBFLUX
            } else {
                WogeSpringAdapter.MVC
            }
        check(selectedAdapter == contextAdapter) {
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

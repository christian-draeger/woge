package dev.woge.spring.boot.autoconfigure

import dev.woge.host.WogeObserver
import io.micrometer.observation.ObservationRegistry
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean

/** Optional semantic observations using Boot's registry and its configured metrics/tracing handlers. */
@AutoConfiguration(
    before = [WogeAutoConfiguration::class],
    afterName = ["org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration"],
)
@ConditionalOnWebApplication
@ConditionalOnClass(ObservationRegistry::class)
public class WogeObservationAutoConfiguration {
    @Bean
    @ConditionalOnBean(ObservationRegistry::class)
    @ConditionalOnMissingBean(WogeObserver::class)
    public fun wogeMicrometerObserver(registry: ObservationRegistry): WogeObserver = MicrometerWogeObserver(registry)
}

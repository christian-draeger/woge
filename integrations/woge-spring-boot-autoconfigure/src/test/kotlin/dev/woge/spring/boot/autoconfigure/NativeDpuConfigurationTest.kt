package dev.woge.spring.boot.autoconfigure

import dev.woge.runtime.NativeDpuMode
import dev.woge.spring.mvc.WogeSpringMvcHandlers
import dev.woge.spring.webflux.WogeWebFluxHandlers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner
import org.springframework.boot.test.context.runner.WebApplicationContextRunner

class NativeDpuConfigurationTest {
    @Test
    fun `all modes bind consistently in MVC and WebFlux factories`() {
        for (mode in NativeDpuMode.entries) {
            val property = "woge.native-dpu.mode=${mode.name.lowercase().replace('_', '-')}"
            ReactiveWebApplicationContextRunner()
                .withClassLoader(FilteredClassLoader("org.springframework.web.servlet"))
                .withConfiguration(AutoConfigurations.of(WogeAutoConfiguration::class.java))
                .withPropertyValues(property)
                .run { context ->
                    assertNull(context.startupFailure)
                    assertEquals(mode, context.getBean(WogeProperties::class.java).nativeDpu.mode)
                    assertNotNull(context.getBean(WogeWebFluxHandlers::class.java))
                }
            WebApplicationContextRunner()
                .withClassLoader(FilteredClassLoader("org.springframework.web.reactive"))
                .withConfiguration(AutoConfigurations.of(WogeAutoConfiguration::class.java))
                .withPropertyValues(property)
                .run { context ->
                    assertNull(context.startupFailure)
                    assertEquals(mode, context.getBean(WogeProperties::class.java).nativeDpu.mode)
                    assertNotNull(context.getBean(WogeSpringMvcHandlers::class.java))
                }
        }
    }
}

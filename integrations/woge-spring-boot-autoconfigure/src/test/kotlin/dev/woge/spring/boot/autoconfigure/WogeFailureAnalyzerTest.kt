package dev.woge.spring.boot.autoconfigure

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.diagnostics.FailureAnalyzer
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.core.io.support.SpringFactoriesLoader

class WogeFailureAnalyzerTest {
    @Test
    fun `Boot discovers analyzer and explains dual stack startup failure`() {
        // Instantiates through Boot's real factory loader, as at application startup.
        val analyzer =
            SpringFactoriesLoader
                .forDefaultResourceLocation(javaClass.classLoader)
                // Boot's own analyzers need constructor arguments that only a running application supplies.
                .load(FailureAnalyzer::class.java, SpringFactoriesLoader.FailureHandler.handleMessage { _, _ -> })
                .single { it is WogeFailureAnalyzer }
        WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WogeAutoConfiguration::class.java))
            .run { context ->
                val analysis = requireNotNull(analyzer.analyze(requireNotNull(context.startupFailure)))
                assertNotNull(analysis)
                assertTrue(analysis.description.contains("both Spring MVC and WebFlux"))
                assertTrue(analysis.action.orEmpty().contains("spring.main.web-application-type"))
            }
        assertNull(analyzer.analyze(IllegalStateException("unrelated application failure")))
    }

    @Test
    fun `invalid limits get an actionable Boot report`() {
        WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WogeAutoConfiguration::class.java))
            .withPropertyValues("woge.adapter=mvc", "woge.deferred.max-concurrency=0")
            .run { context ->
                val analysis = requireNotNull(WogeFailureAnalyzer().analyze(requireNotNull(context.startupFailure)))
                assertNotNull(analysis)
                assertTrue(analysis.description.contains("positive"))
                assertTrue(analysis.description.contains("woge.deferred.max-concurrency"))
            }
    }
}

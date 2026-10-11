package dev.woge.spring.boot.autoconfigure;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Reports Woge startup configuration failures through Spring Boot.
 * Java on purpose: Spring's factory loader needs kotlin-reflect to instantiate Kotlin classes.
 */
public final class WogeFailureAnalyzer extends AbstractFailureAnalyzer<WogeConfigurationException> {
    /** Creates the factory registered in spring.factories. */
    public WogeFailureAnalyzer() {
    }

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, WogeConfigurationException cause) {
        return new FailureAnalysis(
            cause.getMessage(),
            "Use matching Spring and Woge adapter dependencies. Set 'woge.adapter' and "
                + "'spring.main.web-application-type' to the same stack, and use positive Woge timeout/concurrency values.",
            cause
        );
    }
}

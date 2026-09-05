package dev.woge.spring.boot.autoconfigure

/** Spring transport selected for the current application. */
public enum class WogeSpringAdapter {
    /** Infer one unambiguous adapter from the application classpath. */
    AUTO,

    /** Use the non-blocking Spring WebFlux adapter. */
    WEBFLUX,

    /** Use the Servlet-based Spring MVC adapter. */
    MVC,
}

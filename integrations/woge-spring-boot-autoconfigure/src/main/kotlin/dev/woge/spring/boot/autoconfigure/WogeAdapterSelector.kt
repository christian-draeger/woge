package dev.woge.spring.boot.autoconfigure

import org.springframework.util.ClassUtils

internal object WogeAdapterSelector {
    private const val WEBFLUX_FRAMEWORK = "org.springframework.web.reactive.DispatcherHandler"
    private const val MVC_FRAMEWORK = "org.springframework.web.servlet.DispatcherServlet"
    private const val WOGE_WEBFLUX_ADAPTER = "dev.woge.spring.webflux.WogeWebFluxHandlers"
    private const val WOGE_MVC_ADAPTER = "dev.woge.spring.mvc.WogeSpringMvcHandlers"

    fun select(
        requested: WogeSpringAdapter,
        classLoader: ClassLoader,
    ): WogeSpringAdapter {
        val webFluxFramework = ClassUtils.isPresent(WEBFLUX_FRAMEWORK, classLoader)
        val mvcFramework = ClassUtils.isPresent(MVC_FRAMEWORK, classLoader)
        val webFluxAdapter = ClassUtils.isPresent(WOGE_WEBFLUX_ADAPTER, classLoader)
        val mvcAdapter = ClassUtils.isPresent(WOGE_MVC_ADAPTER, classLoader)

        return when (requested) {
            WogeSpringAdapter.AUTO ->
                selectAutomatically(
                    webFluxFramework = webFluxFramework,
                    mvcFramework = mvcFramework,
                    webFluxAdapter = webFluxAdapter,
                    mvcAdapter = mvcAdapter,
                )

            WogeSpringAdapter.WEBFLUX -> {
                requireAvailable(
                    selected = WogeSpringAdapter.WEBFLUX,
                    frameworkAvailable = webFluxFramework,
                    adapterAvailable = webFluxAdapter,
                )
                WogeSpringAdapter.WEBFLUX
            }

            WogeSpringAdapter.MVC -> {
                requireAvailable(
                    selected = WogeSpringAdapter.MVC,
                    frameworkAvailable = mvcFramework,
                    adapterAvailable = mvcAdapter,
                )
                WogeSpringAdapter.MVC
            }
        }
    }

    private fun selectAutomatically(
        webFluxFramework: Boolean,
        mvcFramework: Boolean,
        webFluxAdapter: Boolean,
        mvcAdapter: Boolean,
    ): WogeSpringAdapter {
        check(!(webFluxFramework && mvcFramework)) {
            "Woge found both Spring MVC and WebFlux. Set 'woge.adapter=webflux' or " +
                "'woge.adapter=mvc' and include only the matching Woge adapter dependency."
        }

        return when {
            webFluxFramework -> {
                requireAvailable(WogeSpringAdapter.WEBFLUX, webFluxFramework, webFluxAdapter)
                WogeSpringAdapter.WEBFLUX
            }

            mvcFramework -> {
                requireAvailable(WogeSpringAdapter.MVC, mvcFramework, mvcAdapter)
                WogeSpringAdapter.MVC
            }

            else ->
                error(
                    "Woge requires a Spring web application. Add Spring Boot WebFlux plus " +
                        "'woge-spring-webflux', or Spring MVC plus 'woge-spring-mvc'.",
                )
        }
    }

    private fun requireAvailable(
        selected: WogeSpringAdapter,
        frameworkAvailable: Boolean,
        adapterAvailable: Boolean,
    ) {
        val displayName = if (selected == WogeSpringAdapter.WEBFLUX) "WebFlux" else "MVC"
        val artifact = if (selected == WogeSpringAdapter.WEBFLUX) "woge-spring-webflux" else "woge-spring-mvc"
        check(frameworkAvailable) {
            "Woge adapter '$displayName' was selected but the Spring $displayName web stack is missing."
        }
        check(adapterAvailable) {
            "Woge adapter '$displayName' was selected but '$artifact' is missing from the runtime classpath."
        }
    }
}

package dev.woge.examples.gradient.t06deferredregion

import dev.woge.spring.webflux.WogeWebFluxHandlers
import dev.woge.spring.webflux.webFluxInput
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.coRouter

@Configuration(proxyBeanMethods = false)
public class Routes {
    @Bean
    public fun forecastRoutes(handlers: WogeWebFluxHandlers): RouterFunction<ServerResponse> {
        val forecast = ForecastPage()
        val page = handlers.page(forecast, ForecastRoute)
        val patches = handlers.deferred(forecast, ForecastPatchesRoute.webFluxInput())
        return coRouter {
            GET(ForecastRoute.path, page::handle)
            GET(ForecastPatchesRoute.path, patches::handle)
        }
    }
}

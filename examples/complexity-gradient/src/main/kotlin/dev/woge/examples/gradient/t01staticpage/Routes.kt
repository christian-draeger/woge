package dev.woge.examples.gradient.t01staticpage

import dev.woge.spring.webflux.WogeWebFluxHandlers
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.coRouter

@Configuration(proxyBeanMethods = false)
public class Routes {
    @Bean
    public fun staticPageRoutes(handlers: WogeWebFluxHandlers): RouterFunction<ServerResponse> {
        val page = handlers.page(StaticPage(), StaticPageRoute)
        return coRouter { GET(StaticPageRoute.path, page::handle) }
    }
}

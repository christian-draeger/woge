package dev.woge.examples.gradient.t02component

import dev.woge.spring.webflux.WogeWebFluxHandlers
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.coRouter

@Configuration(proxyBeanMethods = false)
public class Routes {
    @Bean
    public fun projectListRoutes(handlers: WogeWebFluxHandlers): RouterFunction<ServerResponse> {
        val page = handlers.page(ProjectList(), ProjectListRoute)
        return coRouter { GET(ProjectListRoute.path, page::handle) }
    }
}

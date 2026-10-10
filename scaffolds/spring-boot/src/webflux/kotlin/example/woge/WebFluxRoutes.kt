package example.woge

import dev.woge.spring.webflux.WogeWebFluxHandlers
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.coRouter

@Configuration(proxyBeanMethods = false)
public class WebFluxRoutes {
    @Bean
    public fun homeRoutes(
        homePage: HomePage,
        handlers: WogeWebFluxHandlers,
    ): RouterFunction<ServerResponse> {
        val page = handlers.page(homePage, HomeRoute)
        return coRouter {
            GET(HomeRoute.path, page::handle)
        }
    }
}

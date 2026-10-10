package example.tailwind

import dev.woge.html.AssetUrls
import dev.woge.spring.webflux.WogeWebFluxHandlers
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.coRouter

@SpringBootApplication(proxyBeanMethods = false)
public class Application {
    @Bean
    public fun statusPage(assets: AssetUrls): StatusPage = StatusPage(assets)

    @Bean
    public fun routes(
        statusPage: StatusPage,
        handlers: WogeWebFluxHandlers,
    ): RouterFunction<ServerResponse> {
        val page = handlers.page(statusPage, StatusRoute)
        return coRouter { GET(StatusRoute.path, page::handle) }
    }
}

@Suppress("SpreadOperator")
public fun main(args: Array<String>) {
    runApplication<Application>(*args)
}

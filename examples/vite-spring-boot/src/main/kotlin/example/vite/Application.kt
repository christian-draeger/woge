package example.vite

import dev.woge.html.AssetUrls
import dev.woge.spring.webflux.WogeWebFluxHandlers
import dev.woge.vite.ViteAssets
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.coRouter

@SpringBootApplication(proxyBeanMethods = false)
public class Application {
    /** Hashed production files from `wogeAssets`, or the Vite dev server while `wogeDev` runs. */
    @Bean
    public fun viteAssets(assets: AssetUrls): ViteAssets = ViteAssets(assets)

    @Bean
    public fun routes(
        vite: ViteAssets,
        handlers: WogeWebFluxHandlers,
    ): RouterFunction<ServerResponse> {
        val page = handlers.page(ChartPage(vite), ChartRoute)
        return coRouter { GET(ChartRoute.path, page::handle) }
    }
}

@Suppress("SpreadOperator")
public fun main(args: Array<String>) {
    runApplication<Application>(*args)
}

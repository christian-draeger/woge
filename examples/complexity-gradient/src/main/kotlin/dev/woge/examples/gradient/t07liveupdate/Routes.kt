package dev.woge.examples.gradient.t07liveupdate

import dev.woge.spring.webflux.WogeWebFluxHandlers
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.coRouter

@Configuration(proxyBeanMethods = false)
public class Routes {
    @Bean
    public fun announcementRoutes(handlers: WogeWebFluxHandlers): RouterFunction<ServerResponse> {
        val board = AnnouncementBoard()
        val page = handlers.page(board.page, AnnouncementsRoute)
        val refresh = handlers.page(board.refresh, AnnouncementsRegionRoute)
        val live = handlers.live(board.live, AnnouncementsLiveRoute)
        return coRouter {
            GET(AnnouncementsRoute.path, page::handle)
            GET(AnnouncementsRegionRoute.path, refresh::handle)
            GET(AnnouncementsLiveRoute.path, live::handle)
        }
    }
}

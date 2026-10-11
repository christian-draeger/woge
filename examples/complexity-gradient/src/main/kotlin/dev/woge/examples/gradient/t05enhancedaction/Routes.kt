package dev.woge.examples.gradient.t05enhancedaction

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
    public fun noteRoutes(handlers: WogeWebFluxHandlers): RouterFunction<ServerResponse> {
        val page = handlers.page(NotesPage(), EnhancedNotesRoute)
        val add = handlers.action(AddEnhancedNoteAction, addNoteForm.webFluxInput())
        return coRouter {
            GET(EnhancedNotesRoute.path, page::handle)
            POST(AddEnhancedNoteAction.path, add::handle)
        }
    }
}

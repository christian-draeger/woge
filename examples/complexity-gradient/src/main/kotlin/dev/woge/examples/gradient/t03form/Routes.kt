package dev.woge.examples.gradient.t03form

import dev.woge.examples.gradient.support.SameOriginActionContexts
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
        val page = handlers.page(NotesPage(), NotesRoute)
        val add = handlers.action(AddNoteAction, addNoteForm.webFluxInput(), SameOriginActionContexts)
        return coRouter {
            GET(NotesRoute.path, page::handle)
            POST(AddNoteAction.path, add::handle)
        }
    }
}

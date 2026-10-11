package dev.woge.examples.gradient.t04validation

import dev.woge.spring.webflux.WogeWebFluxHandlers
import dev.woge.spring.webflux.webFluxSubmission
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.coRouter

@Configuration(proxyBeanMethods = false)
public class Routes {
    @Bean
    public fun noteRoutes(handlers: WogeWebFluxHandlers): RouterFunction<ServerResponse> {
        val page = handlers.page(NotesPage(), ValidatedNotesRoute)
        val add = handlers.action(addNoteAction, addNoteForm.webFluxSubmission())
        return coRouter {
            GET(ValidatedNotesRoute.path, page::handle)
            POST(AddValidatedNoteAction.path, add::handle)
        }
    }
}

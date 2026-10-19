package example.todo

import dev.woge.spring.webflux.WogeWebFluxHandlers
import dev.woge.spring.webflux.webFluxInput
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.coRouter

@Configuration(proxyBeanMethods = false)
class TodoRoutes {
    @Bean
    fun todosRoutes(handlers: WogeWebFluxHandlers) =
        coRouter {
            val page = handlers.liveRegion(liveTodos, TodosRoute)
            val add = handlers.action(AddTodoAction, addForm.webFluxInput())
            val toggle = handlers.action(ToggleTodoAction, changeForm.webFluxInput())
            val delete = handlers.action(DeleteTodoAction, changeForm.webFluxInput())
            GET(TodosRoute.path, page::handle)
            POST(AddTodoAction.path, add::handle)
            POST(ToggleTodoAction.path, toggle::handle)
            POST(DeleteTodoAction.path, delete::handle)
        }
}

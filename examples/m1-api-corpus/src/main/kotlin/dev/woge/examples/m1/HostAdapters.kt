package dev.woge.examples.m1

import dev.woge.ktor.KtorPageInput
import dev.woge.ktor.WogeKtorHandlers
import dev.woge.spring.mvc.SpringMvcPageInput
import dev.woge.spring.mvc.WogeSpringMvcHandlers
import dev.woge.spring.mvc.pathVariable
import dev.woge.spring.webflux.WebFluxPageInput
import dev.woge.spring.webflux.WogeWebFluxHandlers

internal class HostAdapters(
    private val page: ProjectPage = ProjectPage(),
) {
    fun webFlux() =
        WogeWebFluxHandlers().let { handlers ->
            val input = WebFluxPageInput { request -> ProjectInput(request.pathVariable("project")) }
            handlers.page(page, input) to handlers.deferred(page, input)
        }

    fun springMvc() =
        WogeSpringMvcHandlers().let { handlers ->
            val input = SpringMvcPageInput { request -> ProjectInput(request.pathVariable("project")) }
            handlers.page(page, input) to handlers.deferred(page, input)
        }

    fun ktor() =
        WogeKtorHandlers().let { handlers ->
            val input = KtorPageInput { call -> ProjectInput(requireNotNull(call.parameters["project"])) }
            handlers.page(page, input) to handlers.deferred(page, input)
        }
}

package example.woge

import dev.woge.spring.mvc.SpringMvcPageInput
import dev.woge.spring.mvc.WogeSpringMvcHandlers
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping

@Configuration(proxyBeanMethods = false)
public class MvcRoutes {
    @Bean
    public fun homeRoutes(
        homePage: HomePage,
        handlers: WogeSpringMvcHandlers,
    ): SimpleUrlHandlerMapping =
        SimpleUrlHandlerMapping(
            mapOf("/" to handlers.page(homePage, SpringMvcPageInput { Unit })),
            0,
        )
}

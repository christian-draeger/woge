package dev.woge.spring.mvc

import dev.woge.tck.AdapterTckApplication
import dev.woge.tck.cursorWebSocketPolicy
import dev.woge.tck.tckActionContext
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

@Configuration(proxyBeanMethods = false)
@EnableWebSocket
internal class CursorSocketMvcConfiguration(
    private val application: AdapterTckApplication,
) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        val socket =
            WogeSpringMvcWebSocket(
                cursorWebSocketPolicy(),
                application.cursorSocket,
                SpringMvcRequestContextFactory {
                    tckActionContext(
                        it.getHeader("X-Tck-Subject")?.takeUnless { subject ->
                            subject ==
                                "anonymous"
                        },
                        true,
                    )
                },
            )
        registry.addHandler(socket, "/woge-tck/socket").addInterceptors(socket).setAllowedOrigins("http://woge.test")
    }
}

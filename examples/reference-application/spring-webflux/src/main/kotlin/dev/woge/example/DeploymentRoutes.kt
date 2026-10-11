package dev.woge.example

import dev.woge.example.deployment.DeploymentConfirmAction
import dev.woge.example.deployment.DeploymentLiveRoute
import dev.woge.example.deployment.DeploymentRoute
import dev.woge.example.deployment.ReferenceOperations
import dev.woge.example.deployment.deploymentForm
import dev.woge.example.deployment.deploymentLive
import dev.woge.example.deployment.deploymentPage
import dev.woge.spring.webflux.WogeWebFluxHandlers
import dev.woge.spring.webflux.webFluxInput
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.coRouter

@Configuration(proxyBeanMethods = false)
@Profile("deployment")
public class DeploymentRoutes {
    @Bean
    public fun deploymentHandlers(): WogeWebFluxHandlers =
        WogeWebFluxHandlers(
            maxRegions = ReferenceOperations.maxRegions,
            maxConcurrency = ReferenceOperations.maxConcurrency,
            patchStreamLimits = ReferenceOperations.patchLimits,
            liveLimits = ReferenceOperations.liveLimits,
            renderIdentitySecret = ReferenceOperations.identitySecret,
            observer = ReferenceOperations.observer,
        )

    @Bean
    public fun deploymentHttpRoutes(handlers: WogeWebFluxHandlers): RouterFunction<ServerResponse> {
        val page = handlers.page(deploymentPage, DeploymentRoute)
        val action = handlers.action(DeploymentConfirmAction, deploymentForm.webFluxInput())
        val live = handlers.live(deploymentLive, DeploymentLiveRoute)
        return coRouter {
            GET(DeploymentRoute.path, page::handle)
            POST(DeploymentConfirmAction.path, action::handle)
            GET(DeploymentLiveRoute.path, live::handle)
        }
    }
}

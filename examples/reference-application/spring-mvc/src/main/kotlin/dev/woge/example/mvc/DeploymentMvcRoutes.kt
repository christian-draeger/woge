package dev.woge.example.mvc

import dev.woge.example.deployment.DeploymentConfirmAction
import dev.woge.example.deployment.DeploymentLiveRoute
import dev.woge.example.deployment.DeploymentRoute
import dev.woge.example.deployment.ReferenceOperations
import dev.woge.example.deployment.deploymentForm
import dev.woge.example.deployment.deploymentLive
import dev.woge.example.deployment.deploymentPage
import dev.woge.spring.mvc.WogeSpringMvcHandlers
import dev.woge.spring.mvc.springMvcInput
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping
import kotlin.time.Duration.Companion.seconds

@Configuration(proxyBeanMethods = false)
@Profile("deployment")
public class DeploymentMvcRoutes {
    @Bean
    public fun deploymentHandlers(): WogeSpringMvcHandlers =
        WogeSpringMvcHandlers(
            maxRegions = ReferenceOperations.maxRegions,
            maxConcurrency = ReferenceOperations.maxConcurrency,
            patchStreamLimits = ReferenceOperations.patchLimits,
            liveLimits = ReferenceOperations.liveLimits,
            renderIdentitySecret = ReferenceOperations.identitySecret,
            observer = ReferenceOperations.observer,
            asyncTimeout = 25.seconds,
        )

    @Bean
    public fun deploymentRoutes(handlers: WogeSpringMvcHandlers): SimpleUrlHandlerMapping =
        SimpleUrlHandlerMapping(
            mapOf(
                DeploymentRoute.path to handlers.page(deploymentPage, DeploymentRoute),
                DeploymentConfirmAction.path to
                    handlers.action(DeploymentConfirmAction, deploymentForm.springMvcInput()),
                DeploymentLiveRoute.path to handlers.live(deploymentLive, DeploymentLiveRoute),
            ),
            0,
        )
}

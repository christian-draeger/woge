package dev.woge.ktor

import dev.woge.host.RenderIdentitySecret
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.config.ApplicationConfig
import java.util.Base64

private val logger = KotlinLogging.logger(WogeKtorHandlers::class.java.name)

internal fun ApplicationConfig.renderIdentitySecret(): RenderIdentitySecret {
    val configured = propertyOrNull("woge.render-identity.secret")?.getString()
    if (configured == null) {
        logger.warn {
            "woge.render-identity.secret is unset; using a process-local secret. " +
                "Configure a shared secret before running multiple instances or restarting enhanced pages."
        }
        return RenderIdentitySecret.random()
    }
    return RenderIdentitySecret.of(Base64.getDecoder().decode(configured))
}

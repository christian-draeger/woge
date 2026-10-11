package dev.woge.ktor

import dev.woge.host.RenderIdentitySecret
import io.ktor.server.config.ApplicationConfig
import org.slf4j.LoggerFactory
import java.util.Base64

internal fun ApplicationConfig.renderIdentitySecret(): RenderIdentitySecret {
    val configured = propertyOrNull("woge.render-identity.secret")?.getString()
    if (configured == null) {
        LoggerFactory.getLogger(WogeKtorHandlers::class.java).warn(
            "woge.render-identity.secret is unset; using a process-local secret. " +
                "Configure a shared secret before running multiple instances or restarting enhanced pages.",
        )
        return RenderIdentitySecret.random()
    }
    return RenderIdentitySecret.of(Base64.getDecoder().decode(configured))
}

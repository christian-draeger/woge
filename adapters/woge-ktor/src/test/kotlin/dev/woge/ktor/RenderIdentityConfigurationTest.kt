package dev.woge.ktor

import dev.woge.host.IdentityName
import dev.woge.host.PageIdentity
import dev.woge.protocol.PageEpoch
import io.ktor.server.config.MapApplicationConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.Base64

class RenderIdentityConfigurationTest {
    @Test
    fun `Ktor configuration produces stable deployment identities without exposing the secret`() {
        val encoded = Base64.getEncoder().encodeToString(ByteArray(32) { 9 })
        val config = MapApplicationConfig("woge.render-identity.secret" to encoded)
        val first = config.renderIdentitySecret()
        val second = config.renderIdentitySecret()
        val epoch = PageEpoch.of("page")
        val name = IdentityName.of("summary")
        assertEquals(PageIdentity(epoch, first).root.region(name), PageIdentity(epoch, second).root.region(name))
        assertEquals("RenderIdentitySecret(redacted)", first.toString())
        WogeKtorHandlers(config)
    }

    @Test
    fun `invalid configured secrets fail and unset development secrets are independently random`() {
        assertThrows(IllegalArgumentException::class.java) {
            WogeKtorHandlers(MapApplicationConfig("woge.render-identity.secret" to "short"))
        }
        val config = MapApplicationConfig()
        val epoch = PageEpoch.of("page")
        val name = IdentityName.of("summary")
        assertNotEquals(
            PageIdentity(epoch, config.renderIdentitySecret()).root.region(name),
            PageIdentity(epoch, config.renderIdentitySecret()).root.region(name),
        )
    }
}

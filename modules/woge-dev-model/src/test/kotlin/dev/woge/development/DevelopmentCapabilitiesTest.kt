package dev.woge.development

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalWogeDevelopmentApi::class)
class DevelopmentCapabilitiesTest {
    @Test
    fun `capability requests keep reload and restart commands distinct`() {
        AwaitBuildRequest(afterBuild = BuildId.FIRST, timeout = 30.seconds)
        DevelopmentReloadCommand(BuildId.FIRST, ReloadLevel.DOCUMENT_REFRESH)
        DevelopmentRestartCommand(BuildId.FIRST, ReloadLevel.SERVER_RESTART)

        assertThrows(IllegalArgumentException::class.java) {
            DevelopmentReloadCommand(BuildId.FIRST, ReloadLevel.SERVER_RESTART)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DevelopmentRestartCommand(BuildId.FIRST, ReloadLevel.DOCUMENT_REFRESH)
        }
    }
}

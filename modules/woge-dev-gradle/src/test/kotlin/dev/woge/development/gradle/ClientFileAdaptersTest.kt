package dev.woge.development.gradle

import dev.woge.development.BuildId
import dev.woge.development.DevelopmentUrl
import dev.woge.development.ExperimentalWogeDevelopmentApi
import dev.woge.development.ReloadLevel
import dev.woge.development.ServerGeneration
import dev.woge.development.client.DevelopmentClientSettings
import dev.woge.development.orchestrator.DevelopmentHostAdapter
import dev.woge.development.orchestrator.DevelopmentHostRestartRequest
import dev.woge.development.orchestrator.DevelopmentHostRestartResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists

@OptIn(ExperimentalWogeDevelopmentApi::class)
class ClientFileAdaptersTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `the file names the build and generation the application serves`() =
        runBlocking {
            val file = directory.resolve("client.properties")
            val clientFile =
                ClientFile(file) { build, generation ->
                    DevelopmentClientSettings(
                        "http://127.0.0.1:1/events",
                        "http://127.0.0.1:1",
                        null,
                        build,
                        generation,
                    )
                }
            var result: DevelopmentHostRestartResult =
                DevelopmentHostRestartResult.Failed(
                    KotlinDiagnostics.parse(emptyList(), directory),
                    previousApplicationRetained = false,
                )
            val host = ClientFileHostAdapter(FakeHost { result }) { clientFile }
            val restart =
                DevelopmentHostRestartRequest(BuildId.of(1), ServerGeneration.of(1), ReloadLevel.SERVER_RESTART)

            host.restart(restart)
            assertFalse(file.exists())

            result = DevelopmentHostRestartResult.Ready(listOf(DevelopmentUrl.local("http://localhost:8080/")))
            host.restart(restart)
            assertEquals(BuildId.of(1), DevelopmentClientSettings.readFrom(file)?.renderedBuild)
            assertEquals(ServerGeneration.of(1), DevelopmentClientSettings.readFrom(file)?.generation)

            val frontend = ClientFileFrontendAdapter { clientFile }
            assertFalse(frontend.apply(BuildId.of(2), ReloadLevel.HOT_FRONTEND_MODULE))
            assertEquals(true, frontend.apply(BuildId.of(2), ReloadLevel.HOT_ASSET))
            assertEquals(BuildId.of(2), DevelopmentClientSettings.readFrom(file)?.renderedBuild)
            assertEquals(true, frontend.apply(BuildId.of(3), ReloadLevel.DOCUMENT_REFRESH))
            assertEquals(BuildId.of(3), DevelopmentClientSettings.readFrom(file)?.renderedBuild)
            assertEquals(ServerGeneration.of(1), DevelopmentClientSettings.readFrom(file)?.generation)
        }

    private class FakeHost(
        private val result: () -> DevelopmentHostRestartResult,
    ) : DevelopmentHostAdapter {
        override suspend fun restart(request: DevelopmentHostRestartRequest): DevelopmentHostRestartResult = result()

        override suspend fun shutdown() = Unit
    }
}

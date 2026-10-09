package dev.woge.development.gradle

import dev.woge.development.BuildId
import dev.woge.development.ExperimentalWogeDevelopmentApi
import dev.woge.development.ReloadLevel
import dev.woge.development.ServerExited
import dev.woge.development.ServerGeneration
import dev.woge.development.client.DevelopmentClientSettings
import dev.woge.development.orchestrator.DevelopmentFrontendAdapter
import dev.woge.development.orchestrator.DevelopmentHostAdapter
import dev.woge.development.orchestrator.DevelopmentHostRestartRequest
import dev.woge.development.orchestrator.DevelopmentHostRestartResult
import kotlinx.coroutines.flow.Flow
import java.nio.file.Path

/**
 * Keeps the client settings file in step with what the application serves.
 *
 * Each page states which build and server generation rendered it. The file is updated before the
 * orchestrator announces the new state, so a reloaded page never claims an older build and the
 * browser does not reload in a loop.
 */
@ExperimentalWogeDevelopmentApi
internal class ClientFile(
    private val file: Path,
    private val settings: (BuildId?, ServerGeneration?) -> DevelopmentClientSettings,
) {
    @Volatile
    private var generation: ServerGeneration? = null

    @Synchronized
    fun rendered(
        buildId: BuildId?,
        newGeneration: ServerGeneration? = generation,
    ) {
        generation = newGeneration
        settings(buildId, newGeneration).writeTo(file)
    }
}

@ExperimentalWogeDevelopmentApi
internal class ClientFileHostAdapter(
    private val delegate: DevelopmentHostAdapter,
    private val clientFile: () -> ClientFile,
) : DevelopmentHostAdapter {
    override val exits: Flow<ServerExited> get() = delegate.exits

    override suspend fun restart(request: DevelopmentHostRestartRequest): DevelopmentHostRestartResult {
        val result = delegate.restart(request)
        if (result is DevelopmentHostRestartResult.Ready) clientFile().rendered(request.buildId, request.generation)
        return result
    }

    override suspend fun shutdown() {
        delegate.shutdown()
    }
}

@ExperimentalWogeDevelopmentApi
internal class ClientFileFrontendAdapter(
    private val clientFile: () -> ClientFile,
) : DevelopmentFrontendAdapter {
    override suspend fun apply(
        buildId: BuildId,
        level: ReloadLevel,
    ): Boolean {
        if (level != ReloadLevel.DOCUMENT_REFRESH) return false
        clientFile().rendered(buildId)
        return true
    }
}

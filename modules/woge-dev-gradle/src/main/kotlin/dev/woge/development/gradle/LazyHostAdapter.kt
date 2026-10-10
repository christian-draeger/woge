package dev.woge.development.gradle

import dev.woge.development.DevelopmentDiagnostic
import dev.woge.development.DevelopmentDiagnosticCode
import dev.woge.development.DevelopmentDiagnosticSeverity
import dev.woge.development.DevelopmentDiagnosticSummary
import dev.woge.development.ExperimentalWogeDevelopmentApi
import dev.woge.development.ServerExited
import dev.woge.development.orchestrator.DevelopmentHostAdapter
import dev.woge.development.orchestrator.DevelopmentHostRestartRequest
import dev.woge.development.orchestrator.DevelopmentHostRestartResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * Creates the real host on the first restart, because the application's main class is only known
 * after the first successful build.
 */
@ExperimentalWogeDevelopmentApi
internal class LazyHostAdapter(
    private val host: WogeDevelopmentHost = WogeDevelopmentHost.SPRING_BOOT,
    private val create: () -> DevelopmentHostAdapter?,
) : DevelopmentHostAdapter {
    private val created = CompletableDeferred<DevelopmentHostAdapter>()

    override val exits: Flow<ServerExited> = flow { emitAll(created.await().exits) }

    override suspend fun restart(request: DevelopmentHostRestartRequest): DevelopmentHostRestartResult {
        if (!created.isCompleted) {
            val host = create() ?: return mainClassMissing()
            created.complete(host)
        }
        return created.await().restart(request)
    }

    override suspend fun shutdown() {
        if (created.isCompleted) created.await().shutdown()
    }

    private fun mainClassMissing() =
        DevelopmentHostRestartResult.Failed(
            listOf(
                DevelopmentDiagnostic(
                    DevelopmentDiagnosticCode.of("MAIN-CLASS-NOT-FOUND"),
                    DevelopmentDiagnosticSeverity.ERROR,
                    DevelopmentDiagnosticSummary.of(
                        "Woge could not find the ${host.displayName} main class. ${host.mainClassHint}",
                    ),
                ),
            ),
            previousApplicationRetained = false,
        )
}

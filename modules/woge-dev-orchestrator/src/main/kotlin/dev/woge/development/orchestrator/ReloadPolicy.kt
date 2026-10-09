package dev.woge.development.orchestrator

import dev.woge.development.DevelopmentChange
import dev.woge.development.DevelopmentChangeKind
import dev.woge.development.ExperimentalWogeDevelopmentApi
import dev.woge.development.ReloadLevel

/**
 * The cheapest reload level that is safe for the kind of change.
 *
 * Build adapters may demand more, never less. CSS can update in place; any Kotlin, generated or
 * unknown change needs a new server generation; build configuration needs a new process.
 */
@ExperimentalWogeDevelopmentApi
internal object ReloadPolicy {
    fun minimumFor(changes: Set<DevelopmentChange>): ReloadLevel =
        changes.fold(ReloadLevel.HOT_ASSET) { level, change ->
            ReloadLevel.safest(level, minimumFor(change.kind))
        }

    private fun minimumFor(kind: DevelopmentChangeKind): ReloadLevel =
        when (kind) {
            DevelopmentChangeKind.CSS -> ReloadLevel.HOT_ASSET
            DevelopmentChangeKind.FRONTEND_MODULE -> ReloadLevel.HOT_FRONTEND_MODULE
            DevelopmentChangeKind.KOTLIN_SOURCE,
            DevelopmentChangeKind.GENERATED_SOURCE,
            DevelopmentChangeKind.UNKNOWN,
            -> ReloadLevel.SERVER_RESTART
            DevelopmentChangeKind.BUILD_CONFIGURATION -> ReloadLevel.COLD_RESTART
        }
}

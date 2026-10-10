package dev.woge.development.gradle

import dev.woge.development.BuildId
import dev.woge.development.DevelopmentChange
import dev.woge.development.DevelopmentChangeKind
import dev.woge.development.ExperimentalWogeDevelopmentApi
import dev.woge.development.ReloadLevel
import dev.woge.development.orchestrator.DevelopmentBuildRequest
import dev.woge.development.orchestrator.DevelopmentBuildResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists

@OptIn(ExperimentalWogeDevelopmentApi::class)
class GradleBuildAdapterTest {
    @TempDir
    lateinit var project: Path

    private val request = DevelopmentBuildRequest(BuildId.of(1), emptySet())

    @Test
    fun `a successful command is a successful build`() =
        runBlocking {
            val adapter = GradleBuildAdapter(project, shell("echo compiled"))

            assertInstanceOf(DevelopmentBuildResult.Succeeded::class.java, adapter.build(request))
            assertEquals("compiled", adapter.read())
        }

    @Test
    fun `a build of stylesheet changes only allows a hot asset update`() =
        runBlocking {
            val adapter = GradleBuildAdapter(project, shell("echo compiled"))
            val css = DevelopmentChange(DevelopmentChangeKind.CSS)
            val kotlin = DevelopmentChange(DevelopmentChangeKind.KOTLIN_SOURCE)

            assertEquals(
                ReloadLevel.HOT_ASSET,
                (adapter.build(DevelopmentBuildRequest(BuildId.of(1), setOf(css))) as DevelopmentBuildResult.Succeeded)
                    .requiredReload,
            )
            assertEquals(
                ReloadLevel.DOCUMENT_REFRESH,
                (
                    adapter.build(DevelopmentBuildRequest(BuildId.of(2), setOf(css, kotlin)))
                        as DevelopmentBuildResult.Succeeded
                ).requiredReload,
            )
            assertEquals(
                ReloadLevel.DOCUMENT_REFRESH,
                (adapter.build(request) as DevelopmentBuildResult.Succeeded).requiredReload,
            )
        }

    @Test
    fun `a failing command reports parsed diagnostics and keeps the output for the browser`() =
        runBlocking {
            val adapter = GradleBuildAdapter(project, shell("echo 'e: $project/src/App.kt:2:3 Expecting )'; exit 1"))

            val result = assertInstanceOf(DevelopmentBuildResult.Failed::class.java, adapter.build(request))

            assertEquals(
                "Expecting )",
                result.diagnostics
                    .single()
                    .summary.value,
            )
            assertEquals(
                "src/App.kt",
                result.diagnostics
                    .single()
                    .location
                    ?.path
                    ?.value,
            )
            assertTrue("Expecting )" in adapter.read())
        }

    @Test
    fun `an error in generated code triggers one full regeneration`() =
        runBlocking {
            val generated = "$project/build/generated/ksp/main/kotlin/app/SummaryRegion.kt"
            val script =
                """
                case "${'$'}0" in
                  -Pksp.incremental=false) echo regenerated ;;
                  *) echo "e: file://$generated:4:9 Unresolved reference 'summary'."; exit 1 ;;
                esac
                """.trimIndent()
            val adapter = GradleBuildAdapter(project, shell(script))

            assertInstanceOf(DevelopmentBuildResult.Succeeded::class.java, adapter.build(request))
            assertEquals("regenerated", adapter.read())
        }

    @Test
    fun `an error that survives regeneration is reported in the generated file`() =
        runBlocking {
            val generated = "$project/build/generated/ksp/main/kotlin/app/SummaryRegion.kt"
            val adapter = GradleBuildAdapter(project, shell("echo 'e: file://$generated:4:9 Broken'; exit 1"))

            val result = assertInstanceOf(DevelopmentBuildResult.Failed::class.java, adapter.build(request))

            val diagnostic = result.diagnostics.single()
            assertEquals(KotlinDiagnostics.generatedCode, diagnostic.code)
            assertEquals("Generated code does not compile: Broken", diagnostic.summary.value)
            assertEquals("build/generated/ksp/main/kotlin/app/SummaryRegion.kt", diagnostic.location?.path?.value)
        }

    @Test
    fun `output is bounded`() =
        runBlocking {
            val adapter =
                GradleBuildAdapter(project, shell("for i in $(seq 1 500); do echo line-\$i; done"), detailsLimit = 100)

            adapter.build(request)

            assertTrue(adapter.read().length <= 100)
            assertTrue(adapter.read().endsWith("line-500"))
        }

    @Test
    fun `cancelling a build stops the process`() =
        runBlocking {
            val marker = project.resolve("finished")
            val adapter = GradleBuildAdapter(project, shell("sleep 5; touch '$marker'"))

            val build = async(Dispatchers.Default) { adapter.build(request) }
            delay(300)
            withTimeout(5_000) { build.cancelAndJoin() }
            delay(5_500)

            assertFalse(marker.exists())
        }

    private fun shell(script: String): List<String> = listOf("/bin/sh", "-c", script)
}

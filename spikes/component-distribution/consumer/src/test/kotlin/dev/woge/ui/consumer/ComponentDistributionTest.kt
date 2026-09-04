package dev.woge.ui.consumer

import dev.woge.ui.binary.PackagedBoardTheme
import dev.woge.ui.binary.PackagedProjectBoard
import dev.woge.ui.headless.HeadlessProjectBoard
import dev.woge.ui.headless.ProjectBoardDensity
import dev.woge.ui.headless.ProjectBoardModel
import dev.woge.ui.headless.ProjectBoardOptions
import dev.woge.ui.headless.ProjectItem
import dev.woge.ui.headless.ProjectStatus
import dev.woge.ui.headless.stableScopeId
import dev.woge.ui.registry.projectboard.ProjectBoardStyle
import dev.woge.ui.registry.projectboard.SourceOwnedProjectBoard
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ComponentDistributionTest {
    private val model = ProjectBoardModel(
        instanceKey = "delivery",
        heading = "Delivery <portfolio>",
        query = "alpha\" onfocus=\"alert(1)",
        csrfToken = "safe\"<token>",
        projects = listOf(
            ProjectItem(1, "Search & discovery", "Ada", ProjectStatus.ACTIVE, 72),
            ProjectItem(2, "Billing <migration>", "Lin", ProjectStatus.AT_RISK, 34),
        ),
    )

    @Test
    fun `headless primitive renders a useful progressively enhanced board`() {
        val html = HeadlessProjectBoard.render(
            model,
            ProjectBoardOptions(
                showOwner = true,
                showProgress = true,
                density = ProjectBoardDensity.COMPACT,
            ),
        )

        assertContains(html, "<section")
        assertContains(html, "<form method=\"get\" action=\"/projects\"")
        assertContains(html, "<table><caption>Projects</caption>")
        assertContains(html, "<progress max=\"100\" value=\"72\"")
        assertContains(html, "<form method=\"post\" action=\"/projects/1/archive\"")
        assertContains(html, "data-woge-region=\"project-board.delivery.rows\"")
        assertContains(html, "role=\"status\" aria-live=\"polite\"")
        assertContains(html, "Delivery &lt;portfolio&gt;")
        assertContains(html, "alpha&quot; onfocus=&quot;alert(1)")
        assertContains(html, "safe&quot;&lt;token>")
        assertFalse(html.contains("<script", ignoreCase = true))
        assertFalse(html.contains("hydrate", ignoreCase = true))
    }

    @Test
    fun `instance identity and partial rows remain stable without global id collisions`() {
        val other = model.copy(instanceKey = "operations")
        val first = HeadlessProjectBoard.render(model)
        val second = HeadlessProjectBoard.render(other)
        val patch = HybridProjectBoard.renderRowsPatch(model)

        assertContains(first, "id=\"project-board-delivery-heading\"")
        assertContains(second, "id=\"project-board-operations-heading\"")
        assertNotEquals(HeadlessProjectBoard.rowsRegion(model.instanceKey), HeadlessProjectBoard.rowsRegion(other.instanceKey))
        assertContains(patch, "data-woge-region=\"project-board.delivery.rows\"")
        assertContains(HybridProjectBoard.render(model), patch)
        assertEquals(HeadlessProjectBoard.scope, stableScopeId(HeadlessProjectBoard.componentId))
        assertTrue(HeadlessProjectBoard.scope.matches(Regex("w-[0-9a-f]{12}")))
    }

    @Test
    fun `packaged styled component ships its declared assets and typed variants`() {
        val html = PackagedProjectBoard.render(model, PackagedBoardTheme.HIGH_CONTRAST, compact = true)
        val classLoader = PackagedProjectBoard::class.java.classLoader

        assertContains(html, "data-theme=\"high-contrast\"")
        assertContains(html, "data-density=\"compact\"")
        assertContains(html, "data-part=\"owner\"")
        assertNotNull(classLoader.getResource("META-INF/woge/project-board.css"))
        val manifest = assertNotNull(classLoader.getResource("META-INF/woge/components.json")).readText()
        assertContains(manifest, "Apache-2.0")
        assertContains(manifest, PackagedProjectBoard.version)
    }

    @Test
    fun `source-owned component is substantially customized and patchable`() {
        val html = SourceOwnedProjectBoard.render(model)
        val patch = SourceOwnedProjectBoard.renderRows(model)

        assertContains(html, "Aurora delivery portfolio")
        assertContains(html, "<dt>Average progress</dt><dd>53%</dd>")
        assertContains(html, "Projects with owners and delivery progress")
        assertContains(html, patch)
        assertContains(patch, "data-woge-region=\"owned-project-board.delivery.rows\"")
        assertFalse(html.contains("<script", ignoreCase = true))
    }

    @Test
    fun `plain CSS and Tailwind alter presentation only`() {
        val plain = SourceOwnedProjectBoard.render(model, ProjectBoardStyle.PLAIN)
        val tailwind = SourceOwnedProjectBoard.render(model, ProjectBoardStyle.TAILWIND)

        assertNotEquals(plain, tailwind)
        assertEquals(withoutPresentation(plain), withoutPresentation(tailwind))
        assertContains(plain, "/assets/components/project-board.css")
        assertContains(tailwind, "/assets/tailwind.min.css")
    }

    @Test
    fun `invalid model state fails close to its Kotlin source`() {
        val invalidProgress = runCatching {
            ProjectItem(3, "Invalid", "Kai", ProjectStatus.DONE, 101)
        }.exceptionOrNull()
        val invalidIdentity = runCatching {
            model.copy(instanceKey = "Not valid")
        }.exceptionOrNull()

        assertNotNull(invalidProgress)
        assertContains(invalidProgress.message.orEmpty(), "between 0 and 100")
        assertNotNull(invalidIdentity)
        assertContains(invalidIdentity.message.orEmpty(), "instance key")
    }

    private fun withoutPresentation(html: String): String = html
        .replace(Regex(" data-style=\"[^\"]+\""), "")
        .replace(Regex(" data-stylesheet=\"[^\"]+\""), "")
        .replace(Regex(" class=\"[^\"]+\""), "")
}

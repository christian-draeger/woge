package dev.woge.host

import dev.woge.html.HtmlWriter
import dev.woge.html.renderHtml
import dev.woge.protocol.PageEpoch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TypedRegionsTest {
    private val secret = RenderIdentitySecret.of(ByteArray(32) { it.toByte() })
    private val epoch = PageEpoch.of("page-1")

    private fun page() = PageIdentity(epoch, secret)

    @Test
    fun `page region target matches the manual identity path`() {
        val typed = SummaryRegion.target(page()).target
        val manual = page().root.region(IdentityName.of("example.summary"))

        assertEquals(manual, typed)
    }

    @Test
    fun `keyed region target matches the component path and differs by key`() {
        val first = StatusRegion.target(page(), TaskId(1)).target
        val second = StatusRegion.target(page(), TaskId(2)).target
        val manual =
            page()
                .root
                .component(IdentityName.of("example.TaskRow"), IdentityKey.of(1L))
                .children
                .region(IdentityName.of("example.status"))

        assertEquals(manual, first)
        assertNotEquals(first, second)
    }

    @Test
    fun `regions of one component can be addressed together`() {
        val page = page()

        val status = StatusRegion.target(page, TaskId(1)).target
        val title = TitleRegion.target(page, TaskId(1)).target

        assertNotEquals(status, title)
    }

    @Test
    fun `addressing the same region twice on one page fails with guidance`() {
        val page = page()
        StatusRegion.target(page, TaskId(1))

        val failure = assertThrows(DuplicateIdentityException::class.java) { StatusRegion.target(page, TaskId(1)) }

        assertEquals(
            "example.TaskRow > example.status is addressed twice on one page with the same key. " +
                "Address each region once per page; repeated regions need a @WogeKey on their component.",
            failure.message,
        )
    }

    @Test
    fun `region writes the element, data attributes and typed content`() {
        val target = SummaryRegion.target(page())

        val html = renderHtml { region(target, "Ready", elementName = "section") { classes("summary") } }

        assertEquals(
            """<section data-woge-region="${target.target.region.value}" data-woge-revision="0" class="summary">""" +
                "<p>Ready</p></section>",
            html,
        )
        assertEquals("<p>Done</p>", target.render("Done").value)
    }

    @Test
    fun `typed deferred region renders loaded input with the region function`() {
        val target = SummaryRegion.target(page())
        val deferred =
            deferredRegion(target, loading = { text("Loading") }, onFailure = { error("unused") }) { "Loaded" }

        assertEquals(target.target, deferred.target)
        assertEquals("<p>Loaded</p>", runBlocking { deferred.renderContent() }.value)
    }

    @JvmInline
    private value class TaskId(
        val value: Long,
    )

    // Shaped like the code that the Woge KSP processor generates.
    private object SummaryRegion : PageRegion<String>(name = "example.summary") {
        override fun render(
            writer: HtmlWriter,
            input: String,
        ) {
            writer.element("p") { text(input) }
        }
    }

    private object StatusRegion : KeyedRegion<TaskId, Boolean>(name = "example.status", component = "example.TaskRow") {
        override fun identityKey(key: TaskId): IdentityKey = IdentityKey.of(key.value)

        override fun render(
            writer: HtmlWriter,
            input: Boolean,
        ) {
            writer.text(if (input) "done" else "open")
        }
    }

    private object TitleRegion : KeyedRegion<TaskId, String>(name = "example.title", component = "example.TaskRow") {
        override fun identityKey(key: TaskId): IdentityKey = IdentityKey.of(key.value)

        override fun render(
            writer: HtmlWriter,
            input: String,
        ) {
            writer.text(input)
        }
    }
}

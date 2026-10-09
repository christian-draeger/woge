package dev.woge.host

import dev.woge.protocol.PageEpoch
import dev.woge.protocol.PatchTarget
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class RenderIdentityTest {
    private val secret = RenderIdentitySecret.of(ByteArray(32) { it.toByte() })
    private val epoch = PageEpoch.of("page-1")
    private val card = IdentityName.of("ProjectCard")
    private val summary = IdentityName.of("summary")

    private fun page(pageEpoch: PageEpoch = epoch) = PageIdentity(pageEpoch, secret)

    private fun projectSummaries(
        page: PageIdentity,
        keys: List<String>,
    ): Map<String, PatchTarget> =
        keys.associateWith { key ->
            page.root
                .component(card, IdentityKey.of(key))
                .children
                .region(summary)
        }

    @Test
    fun `explicit keys give repeated siblings distinct ids`() {
        val targets = projectSummaries(page(), listOf("woge-1", "woge-2", "woge-3"))

        assertEquals(
            3,
            targets.values
                .map { it.region }
                .toSet()
                .size,
        )
    }

    @Test
    fun `reordered list keeps every id`() {
        val forward = projectSummaries(page(), listOf("a", "b", "c"))
        val reversed = projectSummaries(page(), listOf("c", "b", "a"))

        assertEquals(forward, reversed)
    }

    @Test
    fun `conditional siblings do not shift the ids of others`() {
        val withAll = projectSummaries(page(), listOf("a", "b", "c"))
        val withoutB = projectSummaries(page(), listOf("a", "c"))

        assertEquals(withAll.getValue("a"), withoutB.getValue("a"))
        assertEquals(withAll.getValue("c"), withoutB.getValue("c"))
    }

    @Test
    fun `identity is stable for one epoch and changes with a new epoch`() {
        val first = page().root.region(summary)
        val again = page().root.region(summary)
        val otherEpoch = page(PageEpoch.of("page-2")).root.region(summary)

        assertEquals(first, again)
        assertNotEquals(first.region, otherEpoch.region)
        assertEquals(PageEpoch.of("page-2"), otherEpoch.pageEpoch)
    }

    @Test
    fun `same key under different parents and slots is allowed and distinct`() {
        val root = page().root
        val left = root.component(IdentityName.of("Column"), IdentityKey.of("left")).children
        val right = root.component(IdentityName.of("Column"), IdentityKey.of("right")).children
        val inLeft = left.component(card, IdentityKey.of(7L)).children.region(summary)
        val inRight = right.component(card, IdentityKey.of(7L)).children.region(summary)
        val inAside = left.component(card, IdentityKey.of(7L), slot = IdentityName.of("aside")).children.region(summary)

        assertEquals(3, setOf(inLeft.region, inRight.region, inAside.region).size)
    }

    @Test
    fun `nested regions differ from their parent level`() {
        val root = page().root
        val outer = root.region(summary)
        val inner = root.component(card).children.region(summary)

        assertNotEquals(outer.region, inner.region)
    }

    @Test
    fun `key types are not interchangeable`() {
        val uuid = UUID.fromString("00000000-0000-0000-0000-000000000007")
        val root = page().root
        val ids =
            listOf(IdentityKey.of("7"), IdentityKey.of(7L), IdentityKey.of(uuid))
                .map { key -> root.region(summary, key).region }

        assertEquals(3, ids.toSet().size)
    }

    @Test
    fun `duplicate keys fail with a diagnostic that hides the raw key`() {
        val root = page().root
        root.component(card, IdentityKey.of("customer-4711"))

        val failure =
            assertThrows(DuplicateIdentityException::class.java) {
                root.component(card, IdentityKey.of("customer-4711"))
            }

        assertTrue(failure.message!!.contains("page > ProjectCard[key]"))
        assertTrue(failure.message!!.contains("unique among siblings"))
        assertFalse(failure.message!!.contains("4711"))
    }

    @Test
    fun `repeated sibling without a key asks for one`() {
        val scope = page().root.component(IdentityName.of("Dashboard")).children
        scope.region(summary)

        val failure = assertThrows(DuplicateIdentityException::class.java) { scope.region(summary) }

        assertTrue(failure.message!!.startsWith("page > Dashboard > summary is rendered more than once"))
        assertTrue(failure.message!!.contains("Pass a stable IdentityKey"))
    }

    @Test
    fun `generated ids are short opaque and safe in html attributes`() {
        val target = page().root.region(summary, IdentityKey.of("<script>\"&'"))

        assertTrue(target.region.value.matches(Regex("w1[A-Za-z0-9_-]{24}")), target.region.value)
    }

    @Test
    fun `different secrets give different ids`() {
        val other = PageIdentity(epoch, RenderIdentitySecret.of(ByteArray(32) { 1 }))

        assertNotEquals(page().root.region(summary).region, other.root.region(summary).region)
    }

    @Test
    fun `secrets keys and names reject unsafe input`() {
        assertThrows(IllegalArgumentException::class.java) { RenderIdentitySecret.of(ByteArray(16)) }
        assertThrows(IllegalArgumentException::class.java) { IdentityKey.of("") }
        assertThrows(IllegalArgumentException::class.java) { IdentityName.of("1card") }
        assertThrows(IllegalArgumentException::class.java) { IdentityName.of("card name") }
        assertEquals("RenderIdentitySecret(redacted)", secret.toString())
        assertEquals("IdentityKey(redacted)", IdentityKey.of("secret").toString())
    }
}

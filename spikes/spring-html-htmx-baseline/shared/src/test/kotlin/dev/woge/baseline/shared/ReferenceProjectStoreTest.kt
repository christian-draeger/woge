package dev.woge.baseline.shared

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ReferenceProjectStoreTest {
    @Test
    fun `rejects a blank title without changing the project`() {
        val store = ReferenceProjectStore()

        assertEquals(CreateTaskResult.BlankTitle, store.createTask("woge", "  ", null, null))
        assertEquals(2, store.snapshot("woge")?.tasks?.size)
    }

    @Test
    fun `creates a normalized task and records activity`() {
        val store = ReferenceProjectStore()

        val result = store.createTask("woge", "  Test patches  ", "  Ada  ", LocalDate.parse("2030-01-02"))

        val snapshot = assertIs<CreateTaskResult.Created>(result).snapshot
        assertEquals("Test patches", snapshot.tasks.last().title)
        assertEquals("Ada", snapshot.tasks.last().owner)
        assertEquals("Task created: Test patches", snapshot.activity.first().description)
    }

    @Test
    fun `does not expose an unknown project`() {
        val store = ReferenceProjectStore()

        assertNull(store.identity("missing"))
        assertNull(store.snapshot("missing"))
    }
}

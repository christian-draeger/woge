package dev.woge.spike.references

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

public class ReferenceModelTest {
    @Test
    public fun `page links and actions remain different descriptor kinds`() {
        val url = ProjectPage.href(ProjectPage.Parameters(ProjectSlug("woge docs"), status = "open"))
        val action = CreateTaskAction.invoke(CreateTaskCommand("Document typed references"))

        assertEquals("/projects/woge%20docs?status=open", url.value)
        assertEquals("action.create-task.v1", action.action.id.value)
    }

    @Test
    public fun `one component type creates distinct keyed rendered instances`() {
        val first = TaskRow.instance(TaskId(1), TaskRowProps("First", completed = false))
        val second = TaskRow.instance(TaskId(2), TaskRowProps("Second", completed = true))

        assertEquals(TaskRow, first.component)
        assertEquals(TaskRow, second.component)
        assertNotEquals(first.canonicalKey, second.canonicalKey)
    }

    @Test
    public fun `region slots accept only their owner type and region input`() {
        val first = TaskRow.instance(TaskId(1), TaskRowProps("First", completed = false))
        val second = TaskRow.instance(TaskId(2), TaskRowProps("Second", completed = true))
        val firstStatus = TaskRow.status.of(first, TaskStatusView(completed = true))
        val secondStatus = TaskRow.status.of(second, TaskStatusView(completed = false))
        val updates = UpdateBuilder()

        updates.replace(firstStatus, TaskStatusView(completed = true))
        updates.replace(secondStatus, TaskStatusView(completed = false))

        assertEquals(2, updates.updates.size)
        assertNotEquals(
            updates.updates[0].target.owner.canonicalKey,
            updates.updates[1].target.owner.canonicalKey,
        )
    }
}

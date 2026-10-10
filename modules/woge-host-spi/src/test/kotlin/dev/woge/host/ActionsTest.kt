package dev.woge.host

import dev.woge.html.applicationUrl
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ActionsTest {
    @Test
    fun `action forwards only its typed command and ingress context`() =
        runBlocking {
            val context =
                RequestContext(
                    RequestMethod.POST,
                    RequestTrace(RequestId.of("request"), CorrelationId.of("trace")),
                    security = RequestSecurity(csrf = CsrfVerification.VERIFIED),
                )
            val action =
                object : ActionDescriptor<String>(ActionId.of("create-task")) {
                    override suspend fun execute(request: PageRequest<String>): PageResult {
                        assertSame(context, request.context)
                        assertEquals("Hello", request.input)
                        return redirect(applicationUrl("/tasks"))
                    }
                }
            assertEquals("/woge-actions/create-task", action.url.value)
            assertEquals(action.path, action.url.value)
            assertEquals(ResponseStatus.SEE_OTHER, action.execute(PageRequest("Hello", context)).metadata.status)
            val registry = ActionRegistry(listOf(action))
            assertSame(action, registry.find(ActionId.of("create-task")))
            assertNull(registry.find(ActionId.of("unknown")))
            assertThrows(IllegalArgumentException::class.java) { ActionRegistry(listOf(action, action)) }
        }

    @Test
    fun `registry snapshots registrations and rejects reflective ID syntax`() {
        listOf("", "Task.create", "../task", "task?command=exec", "a".repeat(129)).forEach { id ->
            assertThrows(IllegalArgumentException::class.java) { ActionId.of(id) }
        }
        val entries = mutableListOf<ActionDescriptor<*>>()
        val registry = ActionRegistry(entries)
        entries +=
            object : ActionDescriptor<Unit>(ActionId.of("noop")) {
                override suspend fun execute(request: PageRequest<Unit>): PageResult = htmlPage { text("OK") }
            }
        assertEquals(emptyList<ActionDescriptor<*>>(), registry.actions)
    }
}

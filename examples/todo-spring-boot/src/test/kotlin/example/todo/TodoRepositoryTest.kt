package example.todo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class TodoRepositoryTest {
    @Test
    fun `mutations and snapshots keep stable identity and filtered counts`() {
        val repository = InMemoryTodoRepository()
        val first = repository.add("  Water herbs  ")
        val second = repository.add("Write tests")
        assertEquals("Water herbs", first.title)
        assertTrue(repository.toggle(first.id))
        assertEquals(listOf(second), repository.snapshot("active").visible)
        assertEquals(listOf(first.copy(completed = true)), repository.snapshot("completed").visible)
        assertEquals(1, repository.snapshot().remaining)
        assertTrue(repository.delete(first.id))
        assertFalse(repository.delete(first.id))
        assertFalse(repository.toggle(first.id))
        repository.reset()
        assertTrue(repository.snapshot().todos.isEmpty())
    }

    @Test
    fun `concurrent additions never lose tasks or reuse an identity`() {
        val repository = InMemoryTodoRepository()
        Executors.newFixedThreadPool(4).use { executor ->
            executor
                .invokeAll((1..100).map { index -> Callable { repository.add("Todo $index") } })
                .forEach { it.get() }
        }
        val snapshot = repository.snapshot()
        assertEquals(100, snapshot.todos.size)
        assertEquals(
            100,
            snapshot.todos
                .map { it.id }
                .distinct()
                .size,
        )
        assertThrows(IllegalArgumentException::class.java) { repository.add(" ") }
        assertThrows(IllegalArgumentException::class.java) { repository.add("x".repeat(MAX_TITLE_LENGTH + 1)) }
    }
}

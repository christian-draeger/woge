package example.todo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

const val MAX_TITLE_LENGTH = 120

data class Todo(
    val id: Long,
    val title: String,
    val completed: Boolean = false,
)

data class TodoSnapshot(
    val todos: List<Todo>,
    val filter: String,
) {
    val visible: List<Todo>
        get() = todos.filter { filter == "all" || it.completed == (filter == "completed") }
    val remaining: Int get() = todos.count { !it.completed }
}

interface TodoRepository {
    fun snapshot(filter: String = "all"): TodoSnapshot

    fun add(title: String): Todo

    fun toggle(id: Long): Boolean

    fun delete(id: Long): Boolean

    fun changes(): Flow<Unit>
}

/** One process, no disk: all reads and writes use the same lock. */
class InMemoryTodoRepository : TodoRepository {
    private val lock = Any()
    private val todos = linkedMapOf<Long, Todo>()
    private var nextId = 1L
    private val revision = MutableStateFlow(0L)

    override fun snapshot(filter: String): TodoSnapshot =
        synchronized(lock) {
            require(filter in FILTERS)
            TodoSnapshot(todos.values.toList(), filter)
        }

    override fun add(title: String): Todo =
        synchronized(lock) {
            val normalized = title.trim()
            require(normalized.isNotEmpty() && normalized.length <= MAX_TITLE_LENGTH)
            Todo(nextId++, normalized).also {
                todos[it.id] = it
                revision.value++
            }
        }

    override fun toggle(id: Long): Boolean =
        synchronized(lock) {
            val existing = todos[id] ?: return false
            todos[id] = existing.copy(completed = !existing.completed)
            revision.value++
            true
        }

    override fun delete(id: Long): Boolean =
        synchronized(lock) {
            if (todos.remove(id) == null) return false
            revision.value++
            true
        }

    override fun changes(): Flow<Unit> = revision.map { }

    fun reset() =
        synchronized(lock) {
            todos.clear()
            nextId = 1
            revision.value++
        }
}

val FILTERS = setOf("all", "active", "completed")
internal val todos = InMemoryTodoRepository()

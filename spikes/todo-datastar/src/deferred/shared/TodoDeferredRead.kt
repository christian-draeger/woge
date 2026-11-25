package example.todo

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger

internal const val TODO_SUMMARY_PATH = "/todo-study/deferred"
internal const val TODO_SUMMARY_DELAY_MS = 360L
internal const val TODO_SUMMARY_UNAVAILABLE = "Todo summary unavailable."

/** Request lifetime probe for the loopback study. Every completion reads the ordinary Todo snapshot. */
internal object TodoDeferredRead {
    private val started = AtomicInteger()
    private val cancelled = AtomicInteger()

    suspend fun load(fail: Boolean): Int {
        started.incrementAndGet()
        try {
            delay(TODO_SUMMARY_DELAY_MS)
            if (fail) throw TodoSummaryUnavailable()
            return todos.snapshot().todos.size
        } catch (error: CancellationException) {
            cancelled.incrementAndGet()
            throw error
        }
    }

    suspend fun loadForDocument(fail: Boolean): Int? =
        try {
            load(fail)
        } catch (_: TodoSummaryUnavailable) {
            null
        }

    fun observations(): String = "${started.get()} ${cancelled.get()}"
}

internal class TodoSummaryUnavailable : RuntimeException()

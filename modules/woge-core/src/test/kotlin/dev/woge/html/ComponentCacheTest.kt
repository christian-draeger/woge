package dev.woge.html

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ComponentCacheTest {
    private val key = ComponentCacheKey("greeting", "hello", ComponentCacheScope.Public)

    @Test
    fun `ttl expires output using injected clock`() {
        val clock = MutableClock(Instant.parse("2026-10-11T00:00:00Z"))
        val cache = ComponentCache(clock = clock)
        val calls = AtomicInteger()
        val first =
            cache.render(
                key,
                ComponentCacheOptions(Duration.ofSeconds(5)),
            ) { text("value-${calls.incrementAndGet()}") }
        clock.advance(Duration.ofSeconds(5))
        val second =
            cache.render(key, ComponentCacheOptions(Duration.ofSeconds(5))) {
                text("value-${calls.incrementAndGet()}")
            }
        assertEquals("value-1", first)
        assertEquals("value-2", second)
    }

    @Test
    fun `key and tag invalidation force rendering`() {
        val cache = ComponentCache()
        val calls = AtomicInteger()

        fun render(tag: String) =
            cache.render(key, ComponentCacheOptions(Duration.ofMinutes(1), tags = setOf(tag))) {
                text("value-${calls.incrementAndGet()}")
            }
        assertEquals("value-1", render("group"))
        assertEquals("value-1", render("group"))
        cache.invalidateTag("group")
        assertEquals("value-2", render("group"))
        cache.invalidate(key)
        assertEquals("value-3", render("group"))
    }

    @Test
    fun `same key rendering is single flight`() {
        val cache = ComponentCache()
        val calls = AtomicInteger()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val first =
                pool.submit<String> {
                    cache.render(key, ComponentCacheOptions(Duration.ofMinutes(1))) {
                        calls.incrementAndGet()
                        entered.countDown()
                        assertTrue(release.await(2, TimeUnit.SECONDS))
                        text("shared")
                    }
                }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            val second =
                pool.submit<String> {
                    cache.render(
                        key,
                        ComponentCacheOptions(Duration.ofMinutes(1)),
                    ) { text("wrong") }
                }
            release.countDown()
            assertEquals("shared", first.get(2, TimeUnit.SECONDS))
            assertEquals("shared", second.get(2, TimeUnit.SECONDS))
            assertEquals(1, calls.get())
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `a joined fill cannot return a revision older than the waiting client`() {
        val cache = ComponentCache()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val first =
                pool.submit<String> {
                    cache.render(key, ComponentCacheOptions(Duration.ofMinutes(1), revision = 1)) {
                        entered.countDown()
                        assertTrue(release.await(2, TimeUnit.SECONDS))
                        text("revision-1")
                    }
                }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            val second =
                pool.submit<String> {
                    cache.render(key, ComponentCacheOptions(Duration.ofMinutes(1), revision = 2, minimumRevision = 2)) {
                        text("revision-2")
                    }
                }
            release.countDown()
            assertEquals("revision-1", first.get(2, TimeUnit.SECONDS))
            assertEquals("revision-2", second.get(2, TimeUnit.SECONDS))
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `personalized public scope is rejected while principal and tenant keys differ`() {
        val cache = ComponentCache()
        assertThrows(IllegalArgumentException::class.java) {
            cache.render(
                key,
                ComponentCacheOptions(Duration.ofMinutes(1), requestIsPersonalized = true),
            ) { text("private") }
        }
        val calls = AtomicInteger()

        fun scoped(scope: ComponentCacheScope) =
            cache.render(
                ComponentCacheKey("greeting", "hello", scope),
                ComponentCacheOptions(Duration.ofMinutes(1), requestIsPersonalized = true),
            ) { text("private-${calls.incrementAndGet()}") }
        assertEquals("private-1", scoped(ComponentCacheScope.Principal("alice")))
        assertEquals("private-2", scoped(ComponentCacheScope.Principal("bob")))
        assertEquals("private-3", scoped(ComponentCacheScope.Tenant("tenant-a")))
        assertEquals("private-4", scoped(ComponentCacheScope.Tenant("tenant-b")))
    }

    @Test
    fun `stale output only serves revisions at least as new as client state`() {
        val clock = MutableClock(Instant.parse("2026-10-11T00:00:00Z"))
        val jobs = mutableListOf<Runnable>()
        val cache = ComponentCache(clock = clock, executor = java.util.concurrent.Executor(jobs::add))
        val calls = AtomicInteger()

        fun render(
            revision: Long,
            minimum: Long,
            swr: Boolean,
        ) = cache.render(
            key,
            ComponentCacheOptions(
                Duration.ofSeconds(1),
                revision = revision,
                minimumRevision = minimum,
                staleWhileRevalidate = swr,
            ),
        ) { text("r$revision-${calls.incrementAndGet()}") }

        assertEquals("r4-1", render(4, 0, false))
        clock.advance(Duration.ofSeconds(1))
        assertEquals("r4-1", render(6, 4, true))
        assertEquals(1, jobs.size)
        jobs.single().run()
        assertEquals("r6-2", render(6, 6, false))
        assertEquals("r7-3", render(7, 7, true))
        assertEquals(3, calls.get())
    }

    @OptIn(UnsafeWogeHtmlApi::class)
    @Test
    fun `events cover hits misses stale output invalidation and unsafe output is rejected`() {
        val clock = MutableClock(Instant.parse("2026-10-11T00:00:00Z"))
        val events = mutableListOf<ComponentCacheEvent>()
        val jobs = mutableListOf<Runnable>()
        val cache =
            ComponentCache(
                clock = clock,
                listener = ComponentCacheListener(events::add),
                executor = java.util.concurrent.Executor(jobs::add),
            )
        cache.render(key, ComponentCacheOptions(Duration.ofSeconds(1))) { text("safe") }
        cache.render(key, ComponentCacheOptions(Duration.ofSeconds(1))) { text("ignored") }
        clock.advance(Duration.ofSeconds(1))
        assertEquals(
            "safe",
            cache.render(
                key,
                ComponentCacheOptions(Duration.ofSeconds(1), staleWhileRevalidate = true),
            ) {
                text("new")
            },
        )
        jobs.single().run()
        cache.invalidate(key)
        assertEquals(
            listOf(
                ComponentCacheEvent.Type.MISS,
                ComponentCacheEvent.Type.HIT,
                ComponentCacheEvent.Type.STALE,
                ComponentCacheEvent.Type.INVALIDATED,
            ),
            events.map(ComponentCacheEvent::type),
        )
        assertThrows(IllegalArgumentException::class.java) {
            cache.render(key, ComponentCacheOptions(Duration.ofMinutes(1))) { raw(unsafeHtml("<b>unsafe</b>")) }
        }
        assertThrows(IllegalArgumentException::class.java) { BoundedComponentCacheStorage(0) }
    }

    @Test
    fun `invalidation during a fill prevents stale result from being stored`() {
        val cache = ComponentCache()
        val calls = AtomicInteger()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val pool = Executors.newSingleThreadExecutor()
        try {
            val first =
                pool.submit<String> {
                    cache.render(key, ComponentCacheOptions(Duration.ofMinutes(1))) {
                        calls.incrementAndGet()
                        entered.countDown()
                        assertTrue(release.await(2, TimeUnit.SECONDS))
                        text("invalidated")
                    }
                }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            cache.invalidate(key)
            release.countDown()
            assertEquals("invalidated", first.get(2, TimeUnit.SECONDS))
            assertEquals(
                "fresh",
                cache.render(key, ComponentCacheOptions(Duration.ofMinutes(1))) {
                    calls.incrementAndGet()
                    text("fresh")
                },
            )
            assertEquals(2, calls.get())
        } finally {
            pool.shutdownNow()
        }
    }

    private class MutableClock(
        private var current: Instant,
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = current

        fun advance(duration: Duration) {
            current = current.plus(duration)
        }
    }
}

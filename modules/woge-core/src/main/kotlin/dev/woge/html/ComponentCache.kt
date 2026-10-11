package dev.woge.html

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.LinkedHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.ForkJoinPool

/** Explicit data and security identity for one rendered component. */
public data class ComponentCacheKey<I>(
    public val component: String,
    public val inputs: I,
    public val scope: ComponentCacheScope,
) {
    init {
        require(component.isNotBlank()) { "Component cache identity must not be blank" }
    }
}

/** Security boundary included in every cache key. */
public sealed interface ComponentCacheScope {
    /** Use only for output that is identical for every visitor. */
    public data object Public : ComponentCacheScope

    /** Separate output by authenticated principal. */
    public data class Principal(
        public val id: String,
    ) : ComponentCacheScope {
        init {
            require(id.isNotBlank()) { "Principal cache scope must not be blank" }
        }
    }

    /** Separate output by tenant. */
    public data class Tenant(
        public val id: String,
    ) : ComponentCacheScope {
        init {
            require(id.isNotBlank()) { "Tenant cache scope must not be blank" }
        }
    }
}

/** Expiry, revision and scope checks for one component render. */
public data class ComponentCacheOptions(
    public val ttl: Duration,
    public val revision: Long = 0,
    public val minimumRevision: Long = 0,
    public val tags: Set<String> = emptySet(),
    public val staleWhileRevalidate: Boolean = false,
    public val requestIsPersonalized: Boolean = false,
) {
    init {
        require(!ttl.isNegative && !ttl.isZero) { "Component cache TTL must be positive" }
        require(revision >= 0 && minimumRevision >= 0) { "Component revisions must not be negative" }
        require(minimumRevision <= revision) {
            "Requested component revision cannot be older than the revision already seen by the client"
        }
    }
}

/** Serialized output created by the safe HTML DSL, with its revision and write time. */
public class CachedComponent internal constructor(
    public val html: String,
    public val revision: Long,
    public val writtenAt: Instant,
)

/** Pluggable storage for rendered components. Implementations should be bounded. */
public interface ComponentCacheStorage {
    public val maxEntries: Int

    public fun get(key: ComponentCacheKey<*>): CachedComponent?

    public fun put(
        key: ComponentCacheKey<*>,
        value: CachedComponent,
    )

    public fun remove(key: ComponentCacheKey<*>)

    public fun removeTag(tag: String)

    public fun associateTags(
        key: ComponentCacheKey<*>,
        tags: Set<String>,
    )
}

/** Bounded, process-local LRU storage used by default. */
public class BoundedComponentCacheStorage(
    override val maxEntries: Int,
) : ComponentCacheStorage {
    private val values =
        LinkedHashMap<ComponentCacheKey<*>, CachedComponent>(CACHE_INITIAL_CAPACITY, CACHE_LOAD_FACTOR, true)
    private val tags = mutableMapOf<String, MutableSet<ComponentCacheKey<*>>>()
    private val keyTags = mutableMapOf<ComponentCacheKey<*>, Set<String>>()

    init {
        require(maxEntries > 0) { "Component cache maxEntries must be positive" }
    }

    @Synchronized
    override fun get(key: ComponentCacheKey<*>): CachedComponent? = values[key]

    @Synchronized
    override fun put(
        key: ComponentCacheKey<*>,
        value: CachedComponent,
    ) {
        values[key] = value
        trim()
    }

    @Synchronized
    override fun remove(key: ComponentCacheKey<*>) {
        values.remove(key)
        keyTags.remove(key)?.forEach { tag ->
            tags[tag]?.let { keys ->
                keys.remove(key)
                if (keys.isEmpty()) tags.remove(tag)
            }
        }
    }

    @Synchronized
    override fun removeTag(tag: String) {
        tags.remove(tag)?.toList()?.forEach { key ->
            values.remove(key)
            keyTags.remove(key)?.forEach { other -> tags[other]?.remove(key) }
        }
    }

    @Synchronized
    override fun associateTags(
        key: ComponentCacheKey<*>,
        values: Set<String>,
    ) {
        keyTags[key]?.forEach { tag -> tags[tag]?.remove(key) }
        val safeTags = values.toSet()
        keyTags[key] = safeTags
        safeTags.forEach { tag -> tags.getOrPut(tag) { linkedSetOf() }.add(key) }
    }

    private fun trim() {
        while (values.size > maxEntries) {
            val eldest = values.keys.first()
            remove(eldest)
        }
    }
}

/** Low-cardinality cache event; input values, scopes and rendered HTML are never exposed. */
public data class ComponentCacheEvent(
    public val type: Type,
    public val component: String,
) {
    public enum class Type { HIT, MISS, STALE, INVALIDATED }
}

/** Receives component cache events. Listener failures never alter rendering. */
public fun interface ComponentCacheListener {
    public fun onEvent(event: ComponentCacheEvent)

    public companion object {
        public val NONE: ComponentCacheListener = ComponentCacheListener { }
    }
}

/** Safe, opt-in cache for HTML rendered with [HtmlWriter]. */
public class ComponentCache(
    private val storage: ComponentCacheStorage = BoundedComponentCacheStorage(DEFAULT_MAX_ENTRIES),
    private val clock: Clock = Clock.systemUTC(),
    private val listener: ComponentCacheListener = ComponentCacheListener.NONE,
    private val executor: Executor = ForkJoinPool.commonPool(),
) {
    init {
        require(storage.maxEntries > 0) { "Component cache storage must declare a positive maxEntries bound" }
    }

    private val fills = ConcurrentHashMap<ComponentCacheKey<*>, CompletableFuture<CachedComponent>>()
    private val keyGenerations = ConcurrentHashMap<ComponentCacheKey<*>, Long>()
    private val tagGenerations = ConcurrentHashMap<String, Long>()

    /** Render once per key at a time and return serialized output from the typed HTML DSL. */
    public fun <I> render(
        key: ComponentCacheKey<I>,
        options: ComponentCacheOptions,
        block: HtmlWriter.() -> Unit,
    ): String {
        require(!(options.requestIsPersonalized && key.scope == ComponentCacheScope.Public)) {
            "Personalized component output requires a Principal or Tenant cache scope"
        }
        val now = clock.instant()
        val stored = storage.get(key)
        val usableRevision = stored != null && stored.revision >= options.minimumRevision
        val fresh =
            stored != null &&
                now.isBefore(stored.writtenAt.plus(options.ttl)) &&
                stored.revision >= options.revision
        val output =
            when {
                fresh -> {
                    emit(ComponentCacheEvent.Type.HIT, key.component)
                    checkNotNull(stored).html
                }
                options.staleWhileRevalidate && usableRevision -> {
                    emit(ComponentCacheEvent.Type.STALE, key.component)
                    refreshInBackground(key, options, block)
                    checkNotNull(stored).html
                }
                else -> {
                    emit(ComponentCacheEvent.Type.MISS, key.component)
                    val completed = await(fill(key, options, block))
                    val current =
                        if (completed.revision >= options.minimumRevision) {
                            completed
                        } else {
                            await(fill(key, options, block))
                        }
                    current.html
                }
            }
        return output
    }

    /** Remove one exact, fully scoped key from the cache. */
    public fun invalidate(key: ComponentCacheKey<*>) {
        keyGenerations.merge(key, 1L, Long::plus)
        storage.remove(key)
        emit(ComponentCacheEvent.Type.INVALIDATED, key.component)
    }

    /** Remove every cached component associated with [tag], across all security scopes. */
    public fun invalidateTag(tag: String) {
        require(tag.isNotBlank()) { "Component cache tag must not be blank" }
        tagGenerations.merge(tag, 1L, Long::plus)
        storage.removeTag(tag)
        emit(ComponentCacheEvent.Type.INVALIDATED, "tag")
    }

    @Suppress("TooGenericExceptionCaught")
    private fun <I> refreshInBackground(
        key: ComponentCacheKey<I>,
        options: ComponentCacheOptions,
        block: HtmlWriter.() -> Unit,
    ) {
        val promise = CompletableFuture<CachedComponent>()
        if (fills.putIfAbsent(key, promise) != null) return
        try {
            executor.execute {
                try {
                    promise.complete(renderAndStore(key, options.revision, options.tags, block))
                } catch (failure: Throwable) {
                    promise.completeExceptionally(failure)
                } finally {
                    fills.remove(key, promise)
                }
            }
        } catch (failure: Throwable) {
            fills.remove(key, promise)
            promise.completeExceptionally(failure)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun <I> fill(
        key: ComponentCacheKey<I>,
        options: ComponentCacheOptions,
        block: HtmlWriter.() -> Unit,
    ): CompletableFuture<CachedComponent> {
        val promise = CompletableFuture<CachedComponent>()
        val existing = fills.putIfAbsent(key, promise)
        if (existing != null) return existing
        try {
            promise.complete(renderAndStore(key, options.revision, options.tags, block))
        } catch (failure: Throwable) {
            promise.completeExceptionally(failure)
        } finally {
            fills.remove(key, promise)
        }
        return promise
    }

    private fun <I> renderAndStore(
        key: ComponentCacheKey<I>,
        revision: Long,
        tags: Set<String>,
        block: HtmlWriter.() -> Unit,
    ): CachedComponent {
        tags.forEach { tag -> require(tag.isNotBlank()) { "Component cache tag must not be blank" } }
        val keyGeneration = keyGenerations[key] ?: 0L
        val tagVersions = tags.associateWith { tagGenerations[it] ?: 0L }
        storage.associateTags(key, tags)
        val sink = BufferedHtmlSink()
        var unsafeHtmlUsed = false
        writeHtmlWithUnsafeTracking(sink, block) { unsafeHtmlUsed = true }
        require(!unsafeHtmlUsed) { "Component cache cannot store output written through UnsafeHtml" }
        val result = CachedComponent(sink.content(), revision, clock.instant())
        if ((keyGenerations[key] ?: 0L) == keyGeneration &&
            tagVersions.all { (tag, version) -> (tagGenerations[tag] ?: 0L) == version }
        ) {
            val existing = storage.get(key)
            if (existing == null || existing.revision <= result.revision) storage.put(key, result)
        }
        return result
    }

    private fun await(fill: CompletableFuture<CachedComponent>): CachedComponent =
        try {
            fill.join()
        } catch (failure: CompletionException) {
            throw (failure.cause ?: failure)
        }

    private fun emit(
        type: ComponentCacheEvent.Type,
        component: String,
    ) {
        runCatching { listener.onEvent(ComponentCacheEvent(type, component)) }
    }

    private companion object {
        const val DEFAULT_MAX_ENTRIES: Int = 256
    }
}

private const val CACHE_INITIAL_CAPACITY: Int = 16
private const val CACHE_LOAD_FACTOR: Float = 0.75f

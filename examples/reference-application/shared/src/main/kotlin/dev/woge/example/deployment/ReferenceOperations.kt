package dev.woge.example.deployment

import dev.woge.host.LiveLimits
import dev.woge.host.PatchStreamLimits
import dev.woge.host.RenderIdentitySecret
import dev.woge.host.WogeObserver
import dev.woge.host.WogeOperationFinished
import java.util.Base64
import kotlin.time.Duration.Companion.seconds

/** Example-owned environment settings; Woge's existing policies remain the budget owners. */
public object ReferenceOperations {
    public val release: String = System.getenv("WOGE_RELEASE") ?: "local"

    init {
        require(release.matches(Regex("[a-zA-Z0-9_-]{1,40}"))) { "Invalid release asset directory" }
    }

    public val identitySecret: RenderIdentitySecret by lazy {
        val encoded =
            requireNotNull(System.getenv("WOGE_RENDER_IDENTITY_SECRET")) {
                "The deployment profile requires WOGE_RENDER_IDENTITY_SECRET shared across nodes"
            }
        RenderIdentitySecret.of(Base64.getDecoder().decode(encoded))
    }
    public val maxRegions: Int = positive("WOGE_MAX_REGIONS", DEFAULT_MAX_REGIONS)
    public val maxConcurrency: Int = positive("WOGE_MAX_CONCURRENCY", DEFAULT_MAX_CONCURRENCY)
    public val patchLimits: PatchStreamLimits =
        PatchStreamLimits(
            maxBytes = positive("WOGE_PATCH_BYTES", DEFAULT_PATCH_BYTES).toLong(),
            maxPatches = positive("WOGE_PATCH_COUNT", DEFAULT_PATCH_COUNT),
        )
    public val liveLimits: LiveLimits =
        LiveLimits(
            maxSubscriptions = positive("WOGE_LIVE_SUBSCRIPTIONS", DEFAULT_LIVE_SUBSCRIPTIONS),
            maxSubscriptionsPerSession =
                positive(
                    "WOGE_LIVE_SESSION_SUBSCRIPTIONS",
                    DEFAULT_LIVE_SESSION_SUBSCRIPTIONS,
                ),
            heartbeat = positive("WOGE_LIVE_HEARTBEAT_SECONDS", 1).seconds,
            maxLifetime = positive("WOGE_LIVE_LIFETIME_SECONDS", DEFAULT_LIVE_LIFETIME_SECONDS).seconds,
        )
    public val observer: WogeObserver =
        WogeObserver { event ->
            if (event is WogeOperationFinished) {
                val limit = event.context.exceededLimit
                System.getLogger("woge.reference.operations").log(
                    System.Logger.Level.INFO,
                    "woge.operation={0} outcome={1} limit={2} threshold={3}",
                    event.operation.semanticName,
                    event.outcome.semanticName,
                    limit?.limit,
                    limit?.threshold,
                )
            }
        }

    private fun positive(
        name: String,
        default: Int,
    ): Int = (System.getenv(name)?.toInt() ?: default).also { require(it > 0) { "$name must be positive" } }

    private const val DEFAULT_MAX_REGIONS = 64
    private const val DEFAULT_MAX_CONCURRENCY = 4
    private const val DEFAULT_PATCH_BYTES = 1024 * 1024
    private const val DEFAULT_PATCH_COUNT = 64
    private const val DEFAULT_LIVE_SUBSCRIPTIONS = 200
    private const val DEFAULT_LIVE_SESSION_SUBSCRIPTIONS = 4
    private const val DEFAULT_LIVE_LIFETIME_SECONDS = 20
}

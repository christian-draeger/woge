package dev.woge.spring.boot.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** Small set of proven Spring Boot settings for the M1 deferred-page path. */
@ConfigurationProperties("woge")
public class WogeProperties {
    /** Adapter selection. AUTO requires exactly one supported Spring web stack. */
    public var adapter: WogeSpringAdapter = WogeSpringAdapter.AUTO

    /** Execution limits for one deferred patch-stream request. */
    public var deferred: Deferred = Deferred()

    /** Servlet-specific response lifecycle settings. */
    public var mvc: Mvc = Mvc()

    /** Bounded execution policy applied by the WebFlux deferred handler factory. */
    public class Deferred {
        /** Maximum number of deferred regions executed concurrently per request. */
        public var maxConcurrency: Int = DEFAULT_MAX_CONCURRENCY

        /** Maximum execution time allowed for one deferred region. */
        public var regionTimeout: Duration = DEFAULT_REGION_TIMEOUT
    }

    /** Spring MVC asynchronous response settings. */
    public class Mvc {
        /** Maximum lifetime of one page or patch-stream response before its coroutine is cancelled. */
        public var asyncTimeout: Duration = DEFAULT_MVC_ASYNC_TIMEOUT
    }

    public companion object {
        /** Default concurrency used by the shared server runtime. */
        public const val DEFAULT_MAX_CONCURRENCY: Int = 8

        /** Conservative first default; applications should tune this from observed latency. */
        public val DEFAULT_REGION_TIMEOUT: Duration = Duration.ofSeconds(30)

        /** Leaves room for the default region timeout plus response setup and terminal framing. */
        public val DEFAULT_MVC_ASYNC_TIMEOUT: Duration = Duration.ofSeconds(60)
    }
}

package dev.woge.html

import java.util.ServiceLoader

/**
 * Marks the hook that lets `wogeDev` add its browser client to a document `head`.
 *
 * Application code must not implement or call it. Production applications never activate it.
 */
@RequiresOptIn(
    message = "Only Woge development tooling may contribute to every document head.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
public annotation class WogeDevelopmentHook

/**
 * Writes development-only elements at the end of every `head` element.
 *
 * Woge loads implementations with [ServiceLoader] only when the JVM runs with
 * `-Dwoge.development=true`, which only the `wogeDev` command sets. Contributions use the normal
 * typed DSL; they cannot replace or rewrite application markup.
 */
@WogeDevelopmentHook
public interface DevelopmentHeadContribution {
    public fun writeTo(head: HtmlWriter)
}

@OptIn(WogeDevelopmentHook::class)
internal object DevelopmentHeadContributions {
    private const val ENABLED_PROPERTY = "woge.development"

    private val contributions: List<DevelopmentHeadContribution> by lazy {
        ServiceLoader
            .load(DevelopmentHeadContribution::class.java, DevelopmentHeadContribution::class.java.classLoader)
            .toList()
    }

    fun writeTo(head: HtmlWriter) {
        if (System.getProperty(ENABLED_PROPERTY) != "true") return
        contributions.forEach { it.writeTo(head) }
    }
}

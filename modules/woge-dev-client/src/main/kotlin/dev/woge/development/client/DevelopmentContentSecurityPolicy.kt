package dev.woge.development.client

import dev.woge.development.ExperimentalWogeDevelopmentApi
import java.net.URI

/**
 * Lets an application's own `Content-Security-Policy` accept the development client.
 *
 * The client loads `client.js` and `overlay.css` from the session's loopback origin and opens an
 * `EventSource` to it. This adds that one origin to `script-src`, `style-src` and `connect-src`
 * (copying `default-src` first when a directive is missing). Everything else stays as the
 * application wrote it. Only `wogeDev` calls this; production responses are never changed.
 */
@ExperimentalWogeDevelopmentApi
public object DevelopmentContentSecurityPolicy {
    private val directives = listOf("script-src", "style-src", "connect-src")

    /** Returns the origin, such as `http://127.0.0.1:35729`, that serves the client for [settings]. */
    public fun originOf(settings: DevelopmentClientSettings): String {
        val uri = URI(settings.assetsUrl)
        return "${uri.scheme}://${uri.host}:${uri.port}"
    }

    /** Returns [policy] with [origin] allowed for the development client. */
    public fun allow(
        policy: String,
        origin: String,
    ): String = add(policy, directives, origin)

    /**
     * Returns [policy] with the Vite dev server at [origin] (such as `http://127.0.0.1:5173`) allowed for
     * scripts, styles and requests, plus its hot-update WebSocket on the same host and port.
     */
    public fun allowVite(
        policy: String,
        origin: String,
    ): String = add(add(policy, directives, origin), listOf("connect-src"), origin.replaceFirst("http://", "ws://"))

    private fun add(
        policy: String,
        directives: List<String>,
        origin: String,
    ): String {
        val parsed =
            policy
                .split(';')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .map { it.split(Regex("\\s+")) }
                .toMutableList()
        val fallback = parsed.firstOrNull { it.first().equals("default-src", ignoreCase = true) }
        directives.forEach { name ->
            val index = parsed.indexOfFirst { it.first().equals(name, ignoreCase = true) }
            when {
                index >= 0 -> parsed[index] = withOrigin(parsed[index], origin)
                fallback != null -> parsed += withOrigin(listOf(name) + fallback.drop(1), origin)
            }
        }
        return parsed.joinToString("; ") { it.joinToString(" ") }
    }

    private fun withOrigin(
        directive: List<String>,
        origin: String,
    ): List<String> {
        val sources = directive.drop(1).filterNot { it.equals("'none'", ignoreCase = true) }
        return if (origin in
            sources
        ) {
            listOf(directive.first()) + sources
        } else {
            listOf(directive.first()) + sources + origin
        }
    }
}

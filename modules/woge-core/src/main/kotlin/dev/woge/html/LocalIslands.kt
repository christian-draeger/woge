package dev.woge.html

/** A deliberately small JSON object boundary: only inert, explicitly selected public string fields. */
public class IslandProps(
    fields: Map<String, String>,
) {
    public val fields: Map<String, String> = fields.toMap()

    internal fun json(): String =
        fields.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "${islandJsonString(key)}:${islandJsonString(value)}"
        }
}

/** A stable browser type and versioned serializer; never serialize server models or secrets wholesale. */
public class IslandType<Props>(
    public val name: String,
    public val propsVersion: Int,
    public val serialize: (Props) -> IslandProps,
) {
    init {
        require(name.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Invalid island type" }
        require(propsVersion > 0) { "Island props version must be positive" }
    }
}

/** Application-owned, versioned deployment artifact. Only path URLs without query/fragment are accepted. */
public class IslandModule(
    public val type: IslandType<*>,
    public val modulePath: String,
    public val assetVersion: String,
    public val mountExport: String = "mount",
) {
    init {
        require(modulePath.matches(Regex("/[A-Za-z0-9_/-]+\\.m?js"))) { "Island modules require a local module path" }
        require(modulePath.split('/').none { it == "." || it == ".." }) { "Invalid island module segments" }
        require(assetVersion.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Invalid island asset version" }
        require(mountExport.matches(Regex("[A-Za-z_$][A-Za-z0-9_$]{0,63}"))) { "Invalid island mount export" }
        require(!modulePath.startsWith("//")) { "Island modules must be same-origin paths" }
    }
}

/** Produces the immutable page allowlist from typed declarations, not from patch markup. */
public class IslandRegistry(
    vararg modules: IslandModule,
) {
    private val entries: List<IslandModule> = modules.toList()

    init {
        require(entries.map { it.type.name }.distinct().size == entries.size) { "Duplicate island type" }
    }

    internal fun json(): String =
        entries.joinToString(prefix = "{", postfix = "}") { entry ->
            val url = islandJsonString("${entry.modulePath}?v=${entry.assetVersion}")
            val export = islandJsonString(entry.mountExport)
            "${islandJsonString(entry.type.name)}:{\"url\":$url,\"mount\":$export," +
                "\"propsVersion\":${entry.type.propsVersion}}"
        }
}

public enum class IslandLoading {
    VISIBLE,
    EAGER,
}

/** Emit once in the page head. No executable inline script, build tool or whole-page hydration is needed. */
public fun HtmlWriter.islandManifest(registry: IslandRegistry) {
    check(!islandManifestWritten) { "Emit the island manifest once per document" }
    islandManifestWritten = true
    rawTextElement(
        "script",
        attributes = {
            attribute("type", "application/json")
            attribute("id", "woge-islands")
        },
        content = registry.json(),
    )
}

/** Selects loading for an island root; visible is the default when this attribute is omitted. */
public fun Attributes.islandLoading(loading: IslandLoading) {
    data("woge-island-loading", loading.name.lowercase())
}

/** Loads only the framework island loader; application modules remain lazy. */
public fun HtmlWriter.islandLoader(nonce: CspNonce? = null) {
    moduleScript(applicationUrl("/assets/woge/islands.js"), nonce = nonce)
}

/** Render useful fallback HTML plus inert root-scoped props. Patches reference types, never module URLs. */
public fun <Props> HtmlWriter.island(
    type: IslandType<Props>,
    props: Props,
    key: String? = null,
    attributes: Attributes.() -> Unit = {},
    fallback: HtmlWriter.() -> Unit,
) {
    require(key == null || key.matches(Regex("[A-Za-z0-9_-]{1,256}"))) { "Invalid island key" }
    val json = "{\"version\":${type.propsVersion},\"value\":${type.serialize(props).json()}}"
    element("div", attributes = {
        data("woge-island", type.name)
        key?.let { data("woge-state-key", it) }
        attributes()
    }) {
        fallback()
        element("template", attributes = { data("woge-island-props", "") }) { text(json) }
    }
}

private fun islandJsonString(value: String): String =
    buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '"', '\\' -> append('\\').append(char)
                '<', '>', '&', '\u2028', '\u2029' -> appendIslandUnicode(char)
                else ->
                    if (char.code < JSON_CONTROL_END || char.isSurrogate()) {
                        appendIslandUnicode(char)
                    } else {
                        append(char)
                    }
            }
        }
        append('"')
    }

private fun StringBuilder.appendIslandUnicode(char: Char) {
    append("\\u")
    append(char.code.toString(JSON_HEX_RADIX).padStart(JSON_ESCAPE_WIDTH, '0'))
}

private const val JSON_HEX_RADIX = 16
private const val JSON_ESCAPE_WIDTH = 4
private const val JSON_CONTROL_END = 32

package dev.woge.css

import org.intellij.lang.annotations.Language

/** A complete standards-native CSS stylesheet. Woge preserves syntax it does not understand. */
@JvmInline
public value class CssStylesheet internal constructor(
    /** Original CSS source, unchanged after scalar-value validation. */
    public val source: String,
)

/** A CSS declaration list suitable for one HTML `style` attribute. */
@JvmInline
public value class CssDeclarations internal constructor(
    /** Original declaration source, unchanged after scalar-value validation. */
    public val source: String,
)

/** Marks ordinary CSS source as a complete stylesheet without parsing or normalizing it. */
public fun stylesheet(
    @Language("CSS") source: String,
): CssStylesheet = CssStylesheet(requireCssScalarValue(source))

/** Marks ordinary CSS source as an inline declaration list with IntelliJ's synthetic rule context. */
public fun declarations(
    @Language(value = "CSS", prefix = ".woge-declaration-list {", suffix = "}") source: String,
): CssDeclarations = CssDeclarations(requireCssScalarValue(source))

private fun requireCssScalarValue(source: String): String {
    require('\u0000' !in source) { "CSS source must not contain U+0000" }
    var index = 0
    while (index < source.length) {
        val character = source[index]
        if (character.isHighSurrogate()) {
            require(index + 1 < source.length && source[index + 1].isLowSurrogate()) {
                "CSS source must not contain an unpaired UTF-16 surrogate"
            }
            index += 2
            continue
        }
        require(!character.isLowSurrogate()) { "CSS source must not contain an unpaired UTF-16 surrogate" }
        index += 1
    }
    return source
}

package woge.css

import org.intellij.lang.annotations.Language

@JvmInline
public value class CssStylesheet internal constructor(public val value: String)

@JvmInline
public value class CssDeclarations internal constructor(public val value: String)

public fun stylesheet(@Language("CSS") source: String): CssStylesheet =
    CssStylesheet(requireCssText(source))

public fun declarations(
    @Language(value = "CSS", prefix = ".woge-declaration-list {", suffix = "}") source: String,
): CssDeclarations = CssDeclarations(requireCssText(source))

public fun styleBlock(css: CssStylesheet): String {
    require(!css.value.contains(STYLE_END, ignoreCase = true)) {
        "A style block cannot contain an HTML </style sequence"
    }
    return "<style>${css.value}</style>"
}

public fun styleAttribute(css: CssDeclarations): String = escapeHtmlAttribute(css.value)

private const val STYLE_END: String = "</style"

private fun requireCssText(source: String): String {
    require('\u0000' !in source) { "CSS cannot contain U+0000" }
    return source
}

private fun escapeHtmlAttribute(value: String): String = buildString(value.length) {
    for (character in value) {
        append(
            when (character) {
                '&' -> "&amp;"
                '"' -> "&quot;"
                '<' -> "&lt;"
                else -> character
            },
        )
    }
}

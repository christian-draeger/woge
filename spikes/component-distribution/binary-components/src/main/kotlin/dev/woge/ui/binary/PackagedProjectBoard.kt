package dev.woge.ui.binary

import dev.woge.ui.headless.HeadlessProjectBoard
import dev.woge.ui.headless.ProjectBoardModel
import dev.woge.ui.headless.ProjectBoardDensity
import dev.woge.ui.headless.ProjectBoardOptions

public enum class PackagedBoardTheme(public val attributeValue: String) {
    DEFAULT("default"),
    HIGH_CONTRAST("high-contrast"),
}

public object PackagedProjectBoard {
    public const val version: String = "0.1.0"
    public const val stylesheet: String = "/assets/woge-binary/project-board.css"

    public fun render(
        model: ProjectBoardModel,
        theme: PackagedBoardTheme = PackagedBoardTheme.DEFAULT,
        compact: Boolean = false,
    ): String {
        val density = if (compact) ProjectBoardDensity.COMPACT else ProjectBoardDensity.COMFORTABLE
        return "<div data-woge-package=\"project-board@$version\" data-theme=\"${theme.attributeValue}\">" +
            HeadlessProjectBoard.render(
                model,
                ProjectBoardOptions(showOwner = true, showProgress = false, density = density),
            ) +
            "</div>"
    }
}

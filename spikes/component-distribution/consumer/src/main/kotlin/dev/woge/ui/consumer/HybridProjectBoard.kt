package dev.woge.ui.consumer

import dev.woge.ui.headless.HeadlessProjectBoard
import dev.woge.ui.headless.ProjectBoardModel
import dev.woge.ui.headless.ProjectBoardDensity
import dev.woge.ui.headless.ProjectBoardOptions

public object HybridProjectBoard {
    public const val registryRecipeVersion: String = "0.1.0"
    public const val stylesheet: String = "/assets/application/hybrid-project-board.css"

    public fun render(model: ProjectBoardModel): String =
        "<div data-app-recipe=\"hybrid-project-board\">" +
            HeadlessProjectBoard.render(
                model,
                ProjectBoardOptions(showOwner = true, showProgress = true, density = ProjectBoardDensity.SPACIOUS),
            ) +
            "</div>"

    public fun renderRowsPatch(model: ProjectBoardModel): String =
        HeadlessProjectBoard.renderRowsRegion(
            model,
            ProjectBoardOptions(showOwner = true, showProgress = true, density = ProjectBoardDensity.SPACIOUS),
        )
}

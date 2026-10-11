package dev.woge.registry

import dev.woge.html.applicationUrl
import dev.woge.html.renderHtml
import dev.woge.registry.css.NavigationLink
import dev.woge.registry.css.alertComponent
import dev.woge.registry.css.announcementRegion
import dev.woge.registry.css.breadcrumb
import dev.woge.registry.css.dialogComponent
import dev.woge.registry.css.drawerComponent
import dev.woge.registry.css.navigationDropdown
import dev.woge.registry.css.navigationMenu
import dev.woge.registry.css.navigationTabs
import dev.woge.registry.css.overlayLink
import dev.woge.registry.css.pagination
import dev.woge.registry.css.popoverComponent
import dev.woge.registry.css.progressComponent
import dev.woge.registry.css.skeletonComponent
import dev.woge.registry.css.toastComponent
import dev.woge.registry.css.tooltipComponent
import dev.woge.ui.LiveRegion
import dev.woge.ui.UiId
import java.io.File
import dev.woge.registry.tailwind.NavigationLink as tailwindNavigationLink
import dev.woge.registry.tailwind.alertComponent as tailwindAlertComponent
import dev.woge.registry.tailwind.announcementRegion as tailwindAnnouncementRegion
import dev.woge.registry.tailwind.breadcrumb as tailwindBreadcrumb
import dev.woge.registry.tailwind.dialogComponent as tailwindDialogComponent
import dev.woge.registry.tailwind.drawerComponent as tailwindDrawerComponent
import dev.woge.registry.tailwind.navigationDropdown as tailwindNavigationDropdown
import dev.woge.registry.tailwind.navigationMenu as tailwindNavigationMenu
import dev.woge.registry.tailwind.navigationTabs as tailwindNavigationTabs
import dev.woge.registry.tailwind.overlayLink as tailwindOverlayLink
import dev.woge.registry.tailwind.pagination as tailwindPagination
import dev.woge.registry.tailwind.popoverComponent as tailwindPopoverComponent
import dev.woge.registry.tailwind.progressComponent as tailwindProgressComponent
import dev.woge.registry.tailwind.skeletonComponent as tailwindSkeletonComponent
import dev.woge.registry.tailwind.toastComponent as tailwindToastComponent
import dev.woge.registry.tailwind.tooltipComponent as tailwindTooltipComponent

private const val UPLOAD_PROGRESS = 25.0

public fun main(args: Array<String>) {
    File(args.single()).apply { mkdirs() }.also { output ->
        output.resolve("css.html").writeText(cssExamples())
        output.resolve("tailwind.html").writeText(tailwindExamples())
    }
}

internal fun cssExamples(): String =
    renderHtml {
        val links =
            listOf(
                NavigationLink("Start", applicationUrl("/navigation/a"), current = true),
                NavigationLink("Next", applicationUrl("/navigation/b")),
            )
        breadcrumb("Breadcrumb", links)
        pagination("Pages", links)
        navigationTabs("Views", links)
        navigationMenu("Main", links)
        navigationDropdown("More", links)
        element("section", attributes = {
            attribute("id", "components")
            attribute("tabindex", "-1")
            data("woge-region", "components")
            data("woge-revision", "0")
        }) {
            overlayLink(UiId("help"), applicationUrl("/navigation/b"), "Open help")
            dialogComponent(UiId("help"), "Help", "Close help") { text("Dialog body") }
            overlayLink(UiId("drawer"), applicationUrl("/navigation/b"), "Open drawer")
            drawerComponent(UiId("drawer"), "Drawer", "Close drawer") { text("Drawer body") }
            popoverComponent(UiId("options"), "Options") { text("Options body") }
            tooltipComponent(UiId("tip"), "Tip", "Helpful description")
            alertComponent("A static problem")
            progressComponent("Uploading", UPLOAD_PROGRESS)
            progressComponent("Loading")
            skeletonComponent()
            toastComponent(UiId("toast")) { text("Saved notification") }
        }
        announcementRegion(UiId("notice"))
        announcementRegion(UiId("urgent"), LiveRegion.ALERT)
    }

internal fun tailwindExamples(): String =
    renderHtml {
        val links =
            listOf(
                tailwindNavigationLink("Start", applicationUrl("/navigation/a"), current = true),
                tailwindNavigationLink("Next", applicationUrl("/navigation/b")),
            )
        tailwindBreadcrumb("Breadcrumb", links)
        tailwindPagination("Pages", links)
        tailwindNavigationTabs("Views", links)
        tailwindNavigationMenu("Main", links)
        tailwindNavigationDropdown("More", links)
        element("section", attributes = {
            attribute("id", "components")
            attribute("tabindex", "-1")
            data("woge-region", "components")
            data("woge-revision", "0")
        }) {
            tailwindOverlayLink(UiId("help"), applicationUrl("/navigation/b"), "Open help")
            tailwindDialogComponent(UiId("help"), "Help", "Close help") { text("Dialog body") }
            tailwindOverlayLink(UiId("drawer"), applicationUrl("/navigation/b"), "Open drawer")
            tailwindDrawerComponent(UiId("drawer"), "Drawer", "Close drawer") { text("Drawer body") }
            tailwindPopoverComponent(UiId("options"), "Options") { text("Options body") }
            tailwindTooltipComponent(UiId("tip"), "Tip", "Helpful description")
            tailwindAlertComponent("A static problem")
            tailwindProgressComponent("Uploading", UPLOAD_PROGRESS)
            tailwindProgressComponent("Loading")
            tailwindSkeletonComponent()
            tailwindToastComponent(UiId("toast")) { text("Saved notification") }
        }
        tailwindAnnouncementRegion(UiId("notice"))
        tailwindAnnouncementRegion(UiId("urgent"), LiveRegion.ALERT)
    }

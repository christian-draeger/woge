package dev.woge.tck

import dev.woge.host.ActionExecutor
import dev.woge.host.FormErrors
import dev.woge.host.FormSubmission
import dev.woge.host.FormValues
import dev.woge.host.PageResult
import dev.woge.host.PageUseCase
import dev.woge.host.actionRegionUpdates
import dev.woge.host.htmlPage
import dev.woge.host.region
import dev.woge.host.withFormValidation
import dev.woge.html.applicationUrl
import dev.woge.html.body
import dev.woge.html.head
import dev.woge.html.html
import dev.woge.html.meta
import dev.woge.html.p
import java.util.concurrent.atomic.AtomicInteger

/** One mutation counter per harness, never shared between servers or contract runs. */
internal class TckActionWorkflow {
    private val mutations = AtomicInteger()
    private val action =
        ActionExecutor<TckActionCommand> { request ->
            val result = TckSubmitAction.execute(request)
            if (result is PageResult.Redirect) {
                val count = mutations.incrementAndGet()
                if (request.input.value == "update") {
                    val page = actionPageIdentity()
                    actionRegionUpdates(applicationUrl("/woge-tck/action-complete")) {
                        replace(ActionCountRegion.target(page), count)
                        replace(ActionStatusRegion.target(page), "Saved")
                    }
                } else {
                    result
                }
            } else {
                result
            }
        }

    val submissions: ActionExecutor<FormSubmission<TckActionCommand>> =
        action.withFormValidation(tckActionValidation)

    val completion: PageUseCase<Unit> =
        PageUseCase {
            val page = actionPageIdentity()
            htmlPage {
                html {
                    head {
                        meta(attributes = {
                            attribute("name", "woge-page-epoch")
                            attribute("content", "tck-action-epoch")
                        })
                    }
                    body {
                        region(ActionCountRegion.target(page), mutations.get(), elementName = "section")
                        region(
                            ActionCommandFormRegion.target(page),
                            FormErrors(FormValues.EMPTY, emptyList()),
                            elementName = "section",
                        )
                        region(ActionStatusRegion.target(page), "", elementName = "p", attributes = {
                            attribute("id", "tck-action-status")
                            attribute("role", "status")
                        })
                        p(attributes = {
                            attribute("id", "tck-action-alert")
                            attribute("role", "alert")
                        }) {}
                    }
                }
            }
        }
}

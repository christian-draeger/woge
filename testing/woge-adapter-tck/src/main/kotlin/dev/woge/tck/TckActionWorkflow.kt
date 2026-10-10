package dev.woge.tck

import dev.woge.host.ActionExecutor
import dev.woge.host.FormSubmission
import dev.woge.host.PageResult
import dev.woge.host.PageUseCase
import dev.woge.host.htmlPage
import dev.woge.host.withFormValidation
import dev.woge.html.p
import java.util.concurrent.atomic.AtomicInteger

/** One mutation counter per harness, never shared between servers or contract runs. */
internal class TckActionWorkflow {
    private val mutations = AtomicInteger()
    private val action =
        ActionExecutor<TckActionCommand> { request ->
            TckSubmitAction.execute(request).also { result ->
                if (result is PageResult.Redirect) mutations.incrementAndGet()
            }
        }

    val submissions: ActionExecutor<FormSubmission<TckActionCommand>> =
        action.withFormValidation(tckActionValidation)

    val completion: PageUseCase<Unit> =
        PageUseCase {
            htmlPage { p { text("Completed mutations: ${mutations.get()}") } }
        }
}

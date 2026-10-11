package dev.woge.example.deployment

import dev.woge.host.CsrfVerification
import dev.woge.host.FailureCategory
import dev.woge.host.FormDecoder
import dev.woge.host.LiveUseCase
import dev.woge.host.PageResult
import dev.woge.host.PageUseCase
import dev.woge.host.RequestContext
import dev.woge.host.WogeAction
import dev.woge.host.WogeRegion
import dev.woge.host.WogeRoute
import dev.woge.host.actionForm
import dev.woge.host.failure
import dev.woge.host.htmlPage
import dev.woge.host.liveSubscription
import dev.woge.host.region
import dev.woge.html.HtmlWriter
import dev.woge.html.a
import dev.woge.html.applicationUrl
import dev.woge.html.body
import dev.woge.html.button
import dev.woge.html.h1
import dev.woge.html.head
import dev.woge.html.html
import dev.woge.html.input
import dev.woge.html.label
import dev.woge.html.main
import dev.woge.html.moduleScript
import dev.woge.html.p
import dev.woge.html.stylesheet
import dev.woge.html.title
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import java.util.UUID

@WogeRoute("/deployment")
public data object DeploymentInput

@WogeRoute("/deployment/live/{epoch}")
public data class DeploymentLiveInput(
    public val epoch: UUID,
)

@Serializable
public data class DeploymentCommand(
    public val text: String,
)

public val deploymentForm: FormDecoder<DeploymentCommand> = FormDecoder(DeploymentCommand.serializer())

/** Stateless confirmation: deliberately no database mutation or automatic mutation replay. */
@WogeAction("deployment-confirm")
public suspend fun deploymentConfirm(
    command: DeploymentCommand,
    context: RequestContext,
): PageResult =
    if (context.csrf != CsrfVerification.VERIFIED) {
        failure(FailureCategory.FORBIDDEN, context.correlationId)
    } else if (command.text.length !in 1..MAX_CONFIRMATION_LENGTH) {
        failure(FailureCategory.BAD_REQUEST, context.correlationId)
    } else {
        context.render.updates(DeploymentRoute.url(DeploymentInput)) {
            replace(DeploymentStatusRegion, command.text)
        }
    }

@WogeRegion
internal fun HtmlWriter.deploymentStatus(message: String) {
    p { text(message) }
}

public val deploymentPage: PageUseCase<DeploymentInput> =
    PageUseCase { request ->
        val session = request.context.render
        val release = ReferenceOperations.release
        htmlPage(maxBytes = 64 * 1024) {
            doctype()
            html(attributes = { attribute("lang", "en") }) {
                head {
                    session.metadata(this)
                    title("Deployment reference")
                    stylesheet(applicationUrl("/assets/releases/$release/notes.css"))
                    moduleScript(applicationUrl("/assets/releases/$release/deployment.js"))
                }
                body {
                    main {
                        h1 { text("Deployment $release") }
                        region(session.target(DeploymentStatusRegion), "Ready")
                        actionForm(DeploymentConfirmAction, attributes = {
                            data("woge-action", "")
                            data(
                                "woge-failure-message",
                                "Could not confirm the result. Open a fresh page before submitting again.",
                            )
                        }) {
                            label(attributes = { attribute("for", "deployment-text") }) { text("Confirmation text") }
                            input {
                                attribute("id", "deployment-text")
                                attribute("name", "text")
                                attribute("value", "Confirmed")
                            }
                            button { text("Confirm") }
                        }
                        a(
                            attributes = { url("href", DeploymentRoute.url(DeploymentInput)) },
                        ) { text("Open a fresh page") }
                    }
                }
            }
        }
    }

public val deploymentLive: LiveUseCase<DeploymentLiveInput> =
    LiveUseCase { request ->
        val target =
            request.context.render
                .resume(request.input.epoch.toString())
                .target(DeploymentStatusRegion)
        liveSubscription(listOf(target), flow { awaitCancellation() })
    }

private const val MAX_CONFIRMATION_LENGTH = 120

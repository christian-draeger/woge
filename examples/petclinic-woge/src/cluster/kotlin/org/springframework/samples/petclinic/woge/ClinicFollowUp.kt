package org.springframework.samples.petclinic.woge

import dev.woge.html.HtmlWriter
import dev.woge.html.a
import dev.woge.html.applicationUrl
import dev.woge.html.button
import dev.woge.html.form
import dev.woge.html.input
import dev.woge.html.label
import dev.woge.html.p
import dev.woge.html.renderHtml
import org.springframework.context.annotation.Profile
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.stereotype.Controller
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.server.ResponseStatusException
import java.net.URI
import java.security.Principal
import java.time.LocalDate
import java.util.UUID

@Controller
@Profile("woge-cluster")
class ClinicFollowUp(
    private val jdbc: JdbcTemplate,
    transactionManager: PlatformTransactionManager,
) {
    private val transactions = TransactionTemplate(transactionManager).apply {
        isolationLevel = TransactionDefinition.ISOLATION_READ_COMMITTED
    }

    @GetMapping(FOLLOW_UP_PATH, produces = [MediaType.TEXT_HTML_VALUE])
    fun open(@PathVariable ownerId: Int, principal: Principal, csrf: CsrfToken): ResponseEntity<String> {
        val current = jdbc.query(
            """
            SELECT follow_up_on, revision FROM clinic_follow_up
            JOIN clinic_live_owner_access USING (owner_id)
            WHERE owner_id = ? AND username = ?
            """.trimIndent(),
            { row, _ -> FollowUpState(row.getObject("follow_up_on", LocalDate::class.java), row.getLong("revision")) },
            ownerId,
            principal.name,
        ).singleOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

        val today = LocalDate.now()
        return html(HttpStatus.OK, renderHtml {
            clinicDocument("Schedule an owner follow-up") {
                p { text(current.date?.let { "Current follow-up: $it" } ?: "No follow-up scheduled.") }
                form(attributes = {
                    url("action", applicationUrl(followUpPath(ownerId)))
                    attribute("method", "post")
                }) {
                    hidden(csrf.parameterName, csrf.token)
                    hidden("requestId", UUID.randomUUID().toString())
                    hidden("expectedRevision", current.revision.toString())
                    label(attributes = { attribute("for", "follow-up-date") }) { text("Follow-up date") }
                    input(attributes = {
                        attribute("id", "follow-up-date")
                        attribute("name", "followUpOn")
                        attribute("type", "date")
                        attribute("min", today.plusDays(1).toString())
                        attribute("value", current.date?.toString().orEmpty())
                        boolean("required")
                    })
                    button { text("Save follow-up") }
                }
            }
        })
    }

    @PostMapping(FOLLOW_UP_PATH, consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    fun schedule(
        @PathVariable ownerId: Int,
        principal: Principal,
        @RequestParam requestId: UUID,
        @RequestParam expectedRevision: Long,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) followUpOn: LocalDate,
    ): ResponseEntity<String> {
        val result = transactions.execute { transaction ->
            val username = principal.name
            val granted = jdbc.query(
                "SELECT 1 FROM clinic_live_owner_access WHERE owner_id = ? AND username = ? FOR KEY SHARE",
                { row, _ -> row.getInt(1) },
                ownerId,
                username,
            ).isNotEmpty()
            if (!granted) return@execute FollowUpResult.NOT_FOUND

            val reserved = jdbc.update(
                """
                INSERT INTO clinic_follow_up_receipt (owner_id, username, request_id, expected_revision, follow_up_on)
                SELECT owner_id, ?, ?, ?, ? FROM clinic_follow_up WHERE owner_id = ?
                ON CONFLICT (owner_id, username, request_id) DO NOTHING
                """.trimIndent(),
                username,
                requestId,
                expectedRevision,
                followUpOn,
                ownerId,
            )
            if (reserved == 0) {
                val existing = jdbc.query(
                    """
                    SELECT expected_revision, follow_up_on FROM clinic_follow_up_receipt
                    WHERE owner_id = ? AND username = ? AND request_id = ?
                    """.trimIndent(),
                    { row, _ ->
                        FollowUpState(row.getObject("follow_up_on", LocalDate::class.java), row.getLong("expected_revision"))
                    },
                    ownerId,
                    username,
                    requestId,
                ).singleOrNull()
                return@execute when (existing) {
                    null -> FollowUpResult.NOT_FOUND
                    FollowUpState(followUpOn, expectedRevision) -> FollowUpResult.SAVED
                    else -> FollowUpResult.REUSED_ID
                }
            }

            val result = when {
                expectedRevision !in 0..<Long.MAX_VALUE || !followUpOn.isAfter(LocalDate.now()) ->
                    FollowUpResult.INVALID
                jdbc.update(
                    "UPDATE clinic_follow_up SET follow_up_on = ?, revision = revision + 1 WHERE owner_id = ? AND revision = ?",
                    followUpOn,
                    ownerId,
                    expectedRevision,
                ) == 1 -> FollowUpResult.SAVED
                else -> FollowUpResult.STALE
            }
            if (result != FollowUpResult.SAVED) transaction.setRollbackOnly()
            result
        }
        return when (checkNotNull(result)) {
            FollowUpResult.SAVED ->
                ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create(followUpPath(ownerId))).build()
            FollowUpResult.NOT_FOUND -> throw ResponseStatusException(HttpStatus.NOT_FOUND)
            FollowUpResult.INVALID -> recovery(ownerId, HttpStatus.BAD_REQUEST, "Enter a future follow-up date.")
            FollowUpResult.REUSED_ID ->
                recovery(ownerId, HttpStatus.CONFLICT, "This request was already saved with different values.")
            FollowUpResult.STALE ->
                recovery(ownerId, HttpStatus.CONFLICT, "The follow-up changed since this form was opened.")
        }
    }

    private fun recovery(ownerId: Int, status: HttpStatus, message: String): ResponseEntity<String> =
        html(status, renderHtml {
            clinicDocument("Review the follow-up date") {
                p { text(message) }
                a(attributes = { url("href", applicationUrl(followUpPath(ownerId))) }) {
                    text("Open the current follow-up")
                }
            }
        })

    private fun html(status: HttpStatus, body: String): ResponseEntity<String> =
        ResponseEntity.status(status).contentType(MediaType.TEXT_HTML).body(body)
}

private data class FollowUpState(val date: LocalDate?, val revision: Long)

private enum class FollowUpResult { SAVED, NOT_FOUND, INVALID, REUSED_ID, STALE }

private const val FOLLOW_UP_PATH = "/woge/cluster/owners/{ownerId}/follow-up"

internal fun followUpPath(ownerId: Int): String = "/woge/cluster/owners/$ownerId/follow-up"

private fun HtmlWriter.hidden(name: String, value: String) {
    input(attributes = {
        attribute("type", "hidden")
        attribute("name", name)
        attribute("value", value)
    })
}

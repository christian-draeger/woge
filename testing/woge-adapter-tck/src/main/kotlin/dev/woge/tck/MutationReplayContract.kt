package dev.woge.tck

import dev.woge.host.RequestMethod
import dev.woge.host.ResponseStatus
import dev.woge.protocol.PatchStreamV1
import java.util.UUID

/** The same real-HTTP replay and independent-authorization matrix runs through every adapter. */
internal suspend fun AdapterTckHttpClient.verifyMutationReplay(expect: (Boolean, String, String) -> Unit) {
    verifyUnverifiedAction(expect)
    val contract = "WOGE-REPLAY-001"
    val headers =
        mapOf(
            "Content-Type" to "application/x-www-form-urlencoded",
            "X-Tck-Subject" to "tck-user",
            "Accept" to PatchStreamV1.MEDIA_TYPE,
        )
    var count = 0
    for ((command, state, changes) in listOf(
        Triple("replay-completed", "COMPLETED", 1),
        Triple("replay-rejected", "REJECTED", 0),
        Triple("replay-ambiguous", "AMBIGUOUS", 1),
        Triple("replay-in-progress", "IN_PROGRESS", 0),
    )) {
        val identity = UUID.randomUUID().toString()
        val requestHeaders = headers + ("Woge-Request-Identity" to identity)
        count += changes
        repeat(2) {
            val response = open(RequestMethod.POST, TckSubmitAction.path, requestHeaders, "value=$command")
            response.body().close()
            expect(response.statusCode() == ResponseStatus.OK.code, contract, "replay response status changed")
            expect(response.header("woge-test-mutation-state") == state, contract, "duplicate state was not preserved")
            expect(response.header("woge-test-mutation-count") == count.toString(), contract, "duplicate mutated twice")
        }

        val forbidden =
            open(
                RequestMethod.POST,
                TckSubmitAction.path,
                requestHeaders + ("X-Tck-Subject" to "other-user"),
                "value=$command",
            )
        forbidden.body().close()
        expect(
            forbidden.statusCode() == ResponseStatus.FORBIDDEN.code,
            "WOGE-AUTH-001",
            "duplicate bypassed authorization",
        )
        val changed = if (command == "replay-completed") "replay-rejected" else "replay-completed"
        val conflict = open(RequestMethod.POST, TckSubmitAction.path, requestHeaders, "value=$changed")
        conflict.body().close()
        expect(
            conflict.header("woge-test-mutation-state") == "CONFLICT",
            contract,
            "identity accepted a different command",
        )
        expect(conflict.header("woge-test-mutation-count") == count.toString(), contract, "conflicting command mutated")
    }
}

private suspend fun AdapterTckHttpClient.verifyUnverifiedAction(expect: (Boolean, String, String) -> Unit) {
    for (accept in listOf("text/html", PatchStreamV1.MEDIA_TYPE)) {
        val response =
            open(
                RequestMethod.POST,
                TckSubmitAction.path,
                mapOf(
                    "X-Tck-Subject" to "tck-user",
                    "X-Tck-Unverified" to "true",
                    "Content-Type" to "application/x-www-form-urlencoded",
                    "Accept" to accept,
                ),
                "value=%GG",
            )
        response.body().use { body ->
            expect(
                response.statusCode() == ResponseStatus.FORBIDDEN.code,
                "WOGE-CSRF-001",
                "unverified action decoded input",
            )
            expect(body.readAllBytes().isEmpty(), "WOGE-CSRF-001", "unverified action exposed validation")
        }
    }
}

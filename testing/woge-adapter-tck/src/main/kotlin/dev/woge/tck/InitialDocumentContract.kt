package dev.woge.tck

import dev.woge.host.RequestMethod
import kotlinx.coroutines.withTimeout

internal suspend fun AdapterTckHttpClient.verifyInitialDocument(
    fixture: InitialDocumentFixture,
    expect: (Boolean, String, String) -> Unit,
) {
    val contract = "initial-document-dpu"
    for ((signal, native) in listOf(
        null to false,
        "woge_dpu=0" to false,
        "woge_dpu=1" to true,
        "woge_dpu=1; woge_dpu=1" to false,
        "woge_dpu=old" to false,
    )) {
        val response =
            open(
                RequestMethod.GET,
                "/woge-tck/dpu/auto/safe",
                signal?.let { mapOf("Cookie" to it) } ?: emptyMap(),
            )
        val markup = response.body().use { it.readAllBytes().toString(Charsets.UTF_8) }
        expect(
            response.statusCode() == dev.woge.host.ResponseStatus.OK.code,
            contract,
            "original document did not succeed",
        )
        expect(markup.contains("<?start") == native, contract, "capability selection diverged")
        expect(markup.contains("<template for=") == native, contract, "replacement transport diverged")
        expect(response.header("cache-control") == "no-store", contract, "document can be cached")
        expect(response.header("vary")?.contains("Cookie") == true, contract, "cookie variation is absent")
    }
    for (scenario in listOf("late-unsafe", "loading-unsafe")) {
        val response = open(RequestMethod.GET, "/woge-tck/dpu/native/$scenario")
        val markup = response.body().use { it.readAllBytes().toString(Charsets.UTF_8) }
        expect(!markup.contains("<template for="), contract, "ineligible replacement reached native parser")
        expect(markup.contains("data-woge-dpu-frame"), contract, "framed fallback is absent")
        expect(markup.contains("View complete results"), contract, "normal HTML navigation disappeared")
    }
    verifyDocumentAdmission(expect)
    val head = open(RequestMethod.HEAD, "/woge-tck/dpu/auto/gated", mapOf("Cookie" to "woge_dpu=1"))
    expect(head.body().use { it.readAllBytes().isEmpty() }, contract, "HEAD collected deferred work")
    expect(!fixture.started.isCompleted, contract, "HEAD started deferred work")
    val response = open(RequestMethod.GET, "/woge-tck/dpu/auto/gated", mapOf("Cookie" to "woge_dpu=1"))
    response.body().use { body ->
        val shell = StringBuilder()
        val chunk = ByteArray(DOCUMENT_SHELL_READ_BYTES)
        while (!shell.contains("Original document footer")) {
            val count = body.read(chunk)
            check(count > 0) { "Original document ended before its loading shell" }
            shell.append(chunk.copyOf(count).toString(Charsets.UTF_8))
        }
        expect(!shell.contains("<template for="), contract, "shell waited for deferred completion")
        withTimeout(DOCUMENT_REGION_WAIT_MILLIS) { fixture.started.await() }
        fixture.release.complete(Unit)
        val remaining = body.readAllBytes().toString(Charsets.UTF_8)
        expect(remaining.contains("<template for="), contract, "replacement was not on the original response")
        expect(remaining.endsWith("</body></html>"), contract, "original document did not close")
    }
}

private fun AdapterTckHttpClient.verifyDocumentAdmission(expect: (Boolean, String, String) -> Unit) {
    val contract = "initial-document-dpu"
    val refused = open(RequestMethod.GET, "/woge-tck/dpu/native/over-limit")
    val refusedBody = refused.body().use { it.readAllBytes().toString(Charsets.UTF_8) }
    expect(
        refused.statusCode() == dev.woge.host.ResponseStatus.INTERNAL_SERVER_ERROR.code,
        contract,
        "over-limit document did not fail before streaming",
    )
    expect(!refusedBody.contains("Initial document results"), contract, "over-limit document committed shell bytes")
}

private const val DOCUMENT_SHELL_READ_BYTES = 256
private const val DOCUMENT_REGION_WAIT_MILLIS = 5000L

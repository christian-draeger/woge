package dev.woge.tck

import dev.woge.host.RequestMethod
import dev.woge.host.ResourceLimit
import dev.woge.host.ResponseStatus
import dev.woge.host.WogeOperationFinished
import dev.woge.host.WogeOutcome
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal const val TCK_PAGE_BYTE_BUDGET: Long = 4

internal suspend fun AdapterTckHttpClient.verifyPageByteBudget(
    fixture: AdapterTckFixtureState,
    expect: (Boolean, String, String) -> Unit,
) {
    val contract = "page-byte-budget"
    val output = ByteArrayOutputStream()
    try {
        val response = open(RequestMethod.GET, AdapterTckRoutes.page(AdapterTckPageScenario.PAGE_BYTE_BUDGET))
        response.body().use { body ->
            val chunk = ByteArray(TCK_PAGE_BYTE_BUDGET.toInt() + 1)
            var count = body.read(chunk)
            while (count >= 0) {
                output.write(chunk, 0, count)
                expect(output.size() <= TCK_PAGE_BYTE_BUDGET, contract, "host wrote bytes beyond the page threshold")
                count = body.read(chunk)
            }
        }
    } catch (_: IOException) {
        // An already committed HTML response may abort its connection.
    }
    withTimeout(5.seconds) {
        while (fixture
                .observations()
                .filterIsInstance<WogeOperationFinished>()
                .none { it.context.exceededLimit?.limit == ResourceLimit.PAGE_BYTES }
        ) {
            delay(1.milliseconds)
        }
    }
    expect(fixture.afterPageBudget.get() == 0, contract, "render continued after exhaustion")
    val diagnostic =
        fixture
            .observations()
            .filterIsInstance<WogeOperationFinished>()
            .single { it.context.exceededLimit?.limit == ResourceLimit.PAGE_BYTES }
    expect(
        diagnostic.outcome == WogeOutcome.REJECTED &&
            diagnostic.context.exceededLimit?.threshold == TCK_PAGE_BYTE_BUDGET,
        contract,
        "missing safe page threshold rejection",
    )
}

internal suspend fun AdapterTckHttpClient.verifyDeferredTaskBudget(
    fixture: AdapterTckFixtureState,
    expect: (Boolean, String, String) -> Unit,
) {
    val contract = "deferred-task-budget"
    val response = open(RequestMethod.GET, "/woge-tck/deferred/task-budget")
    response.body().use { body ->
        expect(
            response.statusCode() == ResponseStatus.SERVICE_UNAVAILABLE.code,
            contract,
            "budget exhaustion did not reject before stream commitment",
        )
        expect(body.readAllBytes().isEmpty(), contract, "rejected admission exposed a patch body")
    }
    expect(fixture.budgetContentCalls.get() == 0, contract, "rejected regions executed")
    val rejected =
        fixture
            .observations()
            .filterIsInstance<WogeOperationFinished>()
            .singleOrNull { it.context.exceededLimit?.limit == ResourceLimit.DEFERRED_TASK_COUNT }
    expect(rejected?.outcome == WogeOutcome.REJECTED, contract, "missing safe limit diagnostic")
    val threshold = rejected?.context?.exceededLimit?.threshold ?: 0
    expect(
        threshold > 0 && fixture.budgetDeclarations.get().toLong() == threshold + 1,
        contract,
        "unbounded declarations were materialized beyond one lookahead",
    )
}

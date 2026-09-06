package dev.woge.spring.mvc

import dev.woge.host.DeferredRegionsUseCase
import dev.woge.host.PageRequest
import dev.woge.protocol.PatchId
import dev.woge.runtime.DeferredRegionExecutor
import dev.woge.runtime.DeferredRegionPolicy
import dev.woge.runtime.encodeDeferredPatchStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.coroutines.CoroutineDispatcher
import org.springframework.web.HttpRequestHandler

/** Executes page-scoped deferred work through one asynchronous Servlet response. */
public class WogeSpringMvcDeferredHandler<Input : Any> internal constructor(
    private val regions: DeferredRegionsUseCase<Input>,
    private val input: SpringMvcPageInput<Input>,
    private val contexts: SpringMvcRequestContextFactory,
    private val dispatcher: CoroutineDispatcher,
    private val asyncTimeoutMillis: Long,
    policy: DeferredRegionPolicy,
) : HttpRequestHandler {
    private val executor = DeferredRegionExecutor(policy)

    /** Re-authorizes the request before writing each completed patch as one flush boundary. */
    override fun handleRequest(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        if (request.method !in DEFERRED_METHODS) {
            response.writeMethodNotAllowed(DEFERRED_METHODS)
            return
        }
        val pageRequest = PageRequest(input.decode(request), contexts.create(request))
        request.launchWogeResponse(response, dispatcher, asyncTimeoutMillis) {
            val declaredRegions = regions.regions(pageRequest).toList()
            var patchNumber = 0
            executor
                .execute(declaredRegions)
                .encodeDeferredPatchStream {
                    patchNumber += 1
                    PatchId.of("deferred-$patchNumber")
                }.writeToServlet(response)
        }
    }

    private companion object {
        val DEFERRED_METHODS: Set<String> = setOf("GET")
    }
}

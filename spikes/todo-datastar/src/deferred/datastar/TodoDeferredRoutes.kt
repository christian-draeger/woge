package datastarbaseline

import example.todo.TODO_SUMMARY_PATH
import example.todo.TODO_SUMMARY_UNAVAILABLE
import example.todo.TodoDeferredRead
import kotlinx.coroutines.flow.flow
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyAndAwait
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.coRouter

@Configuration(proxyBeanMethods = false)
@Profile("todo-study-deferred")
internal class TodoDeferredRoutes {
    @Bean
    fun studyRoutes() = coRouter {
        GET(TODO_SUMMARY_PATH) { request ->
            val complete = request.queryParam("complete").orElse("false") == "true"
            val fail = request.queryParam("fail").orElse("false") == "true"
            val count = if (complete) TodoDeferredRead.loadForDocument(fail) else null
            val nonce = documentNonce()
            val status = if (complete && count == null) HttpStatus.SERVICE_UNAVAILABLE else HttpStatus.OK
            html(status, render("deferred", mapOf(
                "complete" to complete, "fail" to fail, "summary" to summary(count), "nonce" to nonce,
            )), nonce)
        }
        GET("$TODO_SUMMARY_PATH/summary") { request ->
            val fail = request.queryParam("fail").orElse("false") == "true"
            streamResponse(HttpStatus.OK).bodyAndAwait(flow {
                val count = TodoDeferredRead.loadForDocument(fail)
                emit(patchElements(render("deferred-summary", mapOf("summary" to summary(count)))))
            })
        }
        GET("$TODO_SUMMARY_PATH/observations") { ServerResponse.ok().bodyValueAndAwait(TodoDeferredRead.observations()) }
    }
}

private fun summary(count: Int?): String =
    if (count == null) TODO_SUMMARY_UNAVAILABLE else "$count todos on the board."

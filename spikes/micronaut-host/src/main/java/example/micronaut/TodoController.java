package example.micronaut;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.runtime.Micronaut;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import reactor.core.publisher.Flux;

@Controller
@ExecuteOn(TaskExecutors.IO)
public final class TodoController {
    private final TodoDeferredEndpoint deferred = new TodoDeferredEndpoint();

    public static void main(String[] args) {
        Micronaut.run(TodoController.class, args);
    }

    @Secured(SecurityRule.IS_ANONYMOUS)
    @Get(value = "/", produces = MediaType.TEXT_HTML)
    public HttpResponse<byte[]> todo(HttpRequest<?> request, @Nullable Authentication authentication) {
        return TodoEndpoints.render(request, authentication);
    }

    @Secured(SecurityRule.IS_ANONYMOUS)
    @Get(value = "/deferred", produces = MediaType.TEXT_HTML)
    public HttpResponse<Flux<byte[]>> deferred(HttpRequest<?> request, @Nullable Authentication authentication) {
        return deferred.render(request, authentication);
    }

    @Secured(SecurityRule.IS_AUTHENTICATED)
    @Post(value = "/woge-actions/add-todo", consumes = MediaType.APPLICATION_FORM_URLENCODED, produces = MediaType.ALL)
    public HttpResponse<byte[]> add(HttpRequest<?> request, Authentication authentication) {
        return TodoEndpoints.add(request, authentication);
    }
}

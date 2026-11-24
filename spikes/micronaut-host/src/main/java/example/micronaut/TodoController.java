package example.micronaut;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.runtime.Micronaut;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

@Controller
@ExecuteOn(TaskExecutors.IO)
public final class TodoController {
    public static void main(String[] args) {
        Micronaut.run(TodoController.class, args);
    }

    @Get(value = "/", produces = MediaType.TEXT_HTML)
    public HttpResponse<byte[]> todo(HttpRequest<?> request) {
        return TodoReadPage.render(request);
    }
}

package corpus;

import com.vaadin.hilla.BrowserCallable;
import jakarta.annotation.security.PermitAll;

@BrowserCallable
@PermitAll
public class TodoService {
    public void save(EditTodo edit) {}
}

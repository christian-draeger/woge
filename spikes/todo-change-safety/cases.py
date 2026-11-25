"""Pinned incomplete edits to the real Todo implementations."""

WOGE_PAGE = "src/main/kotlin/example/todo/TodoPage.kt"
WOGE_ACTION = "src/main/kotlin/example/todo/TodoActions.kt"
WOGE_ROUTES = "src/main/kotlin/example/todo/TodoRoutes.kt"
KTE_SERVER = "src/main/kotlin/baseline/TodoApplication.kt"
KTE_FORM = "src/main/jte/form.kte"
KTE_BOARD = "src/main/jte/board.kte"
DATA_SERVER = "src/main/kotlin/datastarbaseline/TodoApplication.kt"
DATA_PAGE = "src/main/resources/templates/page.html"
DATA_BOARD = "src/main/resources/templates/board.html"

# Each edit lists the app-relative path, exact existing text, replacement and expected occurrence count.
CASES = {
    "unchanged": ("all", {"woge": [], "kte": [], "datastar": []}),
    "rename-private": ("filter", {
        "woge": [(WOGE_PAGE, "selected", "selectedView", 5)],
        "kte": [(KTE_SERVER, ".filter()", ".selectedView()", 4)],
        "datastar": [(DATA_SERVER, ".filter()", ".selectedView()", 3)],
    }),
    "route-parameter": ("filter", {
        "woge": [(WOGE_PAGE, "val filter: String? = null", "val view: String? = null", 1)],
        "kte": [(KTE_SERVER, 'queryParam("filter")', 'queryParam("view")', 1)],
        "datastar": [(DATA_SERVER, 'queryParam("filter")', 'queryParam("view")', 1)],
    }),
    "field-rename": ("native-add", {
        "woge": [(WOGE_ACTION, "val title: String,", "val summary: String,", 1)],
        "kte": [(KTE_FORM, 'name="title"', 'name="summary"', 1)],
        "datastar": [(DATA_PAGE, 'name="title"', 'name="summary"', 1)],
    }),
    "field-add": ("native-add", {
        "woge": [(WOGE_ACTION, "val title: String,", "val title: String,\n    val priority: Int,", 1)],
        "kte": [(KTE_SERVER, "val fields = request.awaitFormData()", "val fields = request.awaitFormData()\n"
                 '    if (fields.getFirst("priority")?.toIntOrNull() == null) return ServerResponse.badRequest().buildAndAwait()', 1)],
        "datastar": [(DATA_SERVER, "val fields = request.awaitFormData()", "val fields = request.awaitFormData()\n"
                      '    if (fields.getFirst("priority")?.toIntOrNull() == null) return ServerResponse.badRequest().buildAndAwait()', 1)],
    }),
    "field-remove": ("native-add", {
        "woge": [(WOGE_ACTION, "    val title: String,\n", "", 1)],
        "kte": [(KTE_FORM, ' name="title"', "", 1)],
        "datastar": [(DATA_PAGE, ' name="title"', "", 1)],
    }),
    "field-type": ("native-add", {
        "woge": [(WOGE_ACTION, "val title: String,", "val title: Int,", 1)],
        "kte": [(KTE_FORM, '@param title: String = ""', '@param title: Int = ""', 1)],
        "datastar": [(DATA_SERVER, 'fields.getFirst("title").orEmpty()',
                      'fields.getFirst("title")?.toIntOrNull()?.toString().orEmpty()', 1)],
    }),
    "action-binding": ("native-add", {
        "woge": [(WOGE_ROUTES, "POST(AddTodoAction.path", 'POST("/woge-actions/create-todo"', 1)],
        "kte": [(KTE_SERVER, 'POST("/woge-actions/add-todo")', 'POST("/woge-actions/create-todo")', 1)],
        "datastar": [(DATA_SERVER, 'POST("/woge-actions/add-todo")', 'POST("/woge-actions/create-todo")', 1)],
    }),
    "region-target": ("enhanced-add", {
        "woge": [(WOGE_PAGE, "fun HtmlWriter.todoBoard(", "fun HtmlWriter.taskBoard(", 1)],
        "kte": [("src/main/jte/add-result.kte", "outOfBand = true", "outOfBand = false", 1)],
        "datastar": [(DATA_SERVER, 'render("board", mapOf("snapshot" to todos.snapshot(filter)))',
                      'render("board", mapOf("snapshot" to todos.snapshot(filter)))'
                      '.replace("todo-board", "todo-board-drift")', 1)],
    }),
    "repeated-key": ("keys", {
        "woge": [(WOGE_PAGE, 'attribute("data-todo-id", todo.id.toString())', 'attribute("data-todo-id", "row")', 1)],
        "kte": [(KTE_BOARD, 'data-todo-id="${todo.id}"', 'data-todo-id="row"', 1)],
        "datastar": [(DATA_BOARD, 'th:attr="data-todo-id=${todo.id}"', 'data-todo-id="row"', 1)],
    }),
    "optional-value": ("initial", {
        "woge": [(WOGE_PAGE, 'val selected: String get() = filter ?: "all"', 'val selected: String get() = filter', 1)],
        "kte": [(KTE_SERVER, '.orElse("all")', '.orElse(null)', 1)],
        "datastar": [(DATA_SERVER, '.orElse("all")', '.orElse(null)', 1)],
    }),
    "output-context": ("output", {
        "woge": [(WOGE_PAGE, "text(todo.title)", 'text(applicationUrl("/"))', 1)],
        "kte": [(KTE_BOARD, "<span>${todo.title}</span>", "<span>${todo.id}</span>", 1)],
        "datastar": [(DATA_BOARD, 'th:text="${todo.title}"', 'th:text="${todo.id}"', 1)],
    }),
    "invalid-remote-value": ("native-invalid", {
        "woge": [(WOGE_ACTION, "val title = command.title.trim()", "val title = command.title", 1)],
        "kte": [(KTE_SERVER, "title.trim().length", "title.length", 1)],
        "datastar": [(DATA_SERVER, "title.trim().length", "title.length", 1)],
    }),
    "unhandled-outcome": ("enhanced-invalid", {
        "woge": [(WOGE_ACTION, "            errorSummary,", '            FormElementId.of("todo-errors-drift"),', 1)],
        "kte": [("src/main/resources/static/todo-htmx.js", 'if (isValidation(detail.xhr)) document.querySelector("#todo-errors").focus();',
                 'if (isValidation(detail.xhr)) document.querySelector("#todo-title").focus();', 1)],
        "datastar": [(DATA_SERVER, '"hasError" to true, "focus" to true', '"hasError" to true, "focus" to false', 1)],
    }),
}

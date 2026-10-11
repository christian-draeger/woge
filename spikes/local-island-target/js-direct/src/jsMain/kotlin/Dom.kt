package island

private val document: dynamic = js("globalThis.document")
actual fun propsJson(): String = document.getElementById("note-props").textContent as String
actual fun bindInput(onInput: (String) -> Unit) {
    val input = document.getElementById("note-text")
    input.addEventListener("input", { _: dynamic -> onInput(input.value as String) })
    onInput(input.value as String)
}
actual fun showStatus(message: String, invalid: Boolean) {
    document.getElementById("note-counter").textContent = message
    document.getElementById("note-text").setAttribute("aria-invalid", invalid.toString())
}
actual fun ready() { js("globalThis.__islandReady = performance.now()") }

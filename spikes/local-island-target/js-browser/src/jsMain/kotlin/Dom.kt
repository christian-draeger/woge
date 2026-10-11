package island

import kotlinx.browser.document
import org.w3c.dom.HTMLInputElement

actual fun propsJson(): String = document.getElementById("note-props")!!.textContent!!
actual fun bindInput(onInput: (String) -> Unit) {
    val input = document.getElementById("note-text") as HTMLInputElement
    input.addEventListener("input", { onInput(input.value) })
    onInput(input.value)
}
actual fun showStatus(message: String, invalid: Boolean) {
    document.getElementById("note-counter")!!.textContent = message
    document.getElementById("note-text")!!.setAttribute("aria-invalid", invalid.toString())
}
actual fun ready() { js("globalThis.__islandReady = performance.now()") }

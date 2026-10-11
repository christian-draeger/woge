package island

@JsFun("() => document.getElementById('note-props').textContent")
actual external fun propsJson(): String

@JsFun("(onInput) => { const input = document.getElementById('note-text'); input.addEventListener('input', () => onInput(input.value)); onInput(input.value); }")
actual external fun bindInput(onInput: (String) -> Unit)

@JsFun("(message, invalid) => { document.getElementById('note-counter').textContent = message; document.getElementById('note-text').setAttribute('aria-invalid', String(invalid)); }")
actual external fun showStatus(message: String, invalid: Boolean)

@JsFun("() => { globalThis.__islandReady = performance.now(); }")
actual external fun ready()

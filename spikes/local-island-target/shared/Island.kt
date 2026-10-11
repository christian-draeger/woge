package island

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Props(val maxLength: Int)

expect fun propsJson(): String
expect fun bindInput(onInput: (String) -> Unit)
expect fun showStatus(message: String, invalid: Boolean)
expect fun ready()

fun main() {
    CoroutineScope(Dispatchers.Default).launch {
        val props = Json.decodeFromString<Props>(propsJson())
        require(props.maxLength in 1..10000)
        bindInput { text ->
            val invalid = text.isBlank() || text.length > props.maxLength
            showStatus("${text.length}/${props.maxLength} characters" + if (invalid) " — enter a nonblank note within the limit" else "", invalid)
        }
        ready()
    }
}

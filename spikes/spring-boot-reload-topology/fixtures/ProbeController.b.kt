package example.woge

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
internal class ReloadProbeController {
    @GetMapping("/__reload-probe", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun probe(): String = "source-b|${ReloadProbe.value}|pid=${ProcessHandle.current().pid()}"
}

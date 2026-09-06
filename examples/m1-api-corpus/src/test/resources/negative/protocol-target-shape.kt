package dev.woge.invalid

import dev.woge.protocol.PageEpoch
import dev.woge.protocol.PatchTarget
import dev.woge.protocol.RegionTargetId

// WOGE-COMPILE-PROTOCOL-001: page epochs and rendered-region IDs are distinct concepts.
internal val invalidTarget =
    PatchTarget(
        pageEpoch = RegionTargetId.of("epoch"),
        region = PageEpoch.of("region"),
    )
